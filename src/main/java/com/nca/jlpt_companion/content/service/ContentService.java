package com.nca.jlpt_companion.content.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nca.jlpt_companion.content.dto.DeltaResponse;
import com.nca.jlpt_companion.content.dto.VersionResponse;
import com.nca.jlpt_companion.content.repo.ContentRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContentService {

    private final ContentRepo repo;
    private final StringRedisTemplate redis;
    private final ObjectMapper om;

    // =======================
    // Cache key helpers (v1)
    // =======================
    private static String kLatest(String domain, String level) {
        return "v1:content:%s:%s:latest".formatted(norm(domain), lvl(level));
    }
    private static String kDelta(String domain, String level, int since, int to) {
        return "v1:content:%s:%s:delta:%d:%d".formatted(norm(domain), lvl(level), since, to);
    }
    private static String norm(String s) { return s == null ? "_" : s.trim(); }
    private static String lvl(String s)  { return s == null ? "_" : s.trim().toUpperCase(); }

    // ===========
    // Public API
    // ===========
    public VersionResponse getLatestVersion(String domain, String level) {
        domain = norm(domain);
        level  = lvl(level);

        String cacheKey = kLatest(domain, level);
        String cached = safeRedisGet(cacheKey);

        int latestVer;
        if (cached != null) {
            try {
                latestVer = Integer.parseInt(cached);
            } catch (NumberFormatException nfe) {
                log.debug("Invalid cached latest version for {}-{}: {}", domain, level, cached);
                latestVer = 0;
            }
        } else {
            latestVer = repo.findLatestVersionNumber(domain, level);
            safeRedisSet(cacheKey, Integer.toString(latestVer), Duration.ofMinutes(10));
        }

        if (latestVer <= 0) {
            return new VersionResponse(0, null, 0L);
        }

        Long latestId = repo.findLatestVersionId(domain, level);
        if (latestId == null) {
            // seharusnya tidak terjadi bila latestVer > 0, tapi tetap defensif
            return new VersionResponse(0, null, 0L);
        }

        Long size = repo.findDeltaSize(latestId, latestId);
        String sum = repo.findDeltaChecksum(latestId, latestId);
        return new VersionResponse(latestVer, sum, size == null ? 0L : size);
    }

    @SuppressWarnings("unchecked")
    public DeltaResponse getDelta(String domain, String level, int since) {
        domain = norm(domain);
        level  = lvl(level);
        since  = Math.max(0, since); // clamp

        int latestVer = repo.findLatestVersionNumber(domain, level);
        if (latestVer <= 0) {
            // scope kosong
            return new DeltaResponse(0, 0, null, List.of(), List.of(), List.of(), List.of());
        }
        if (since >= latestVer) {
            // up-to-date, tidak perlu kirim payload
            return new DeltaResponse(since, latestVer, null, List.of(), List.of(), List.of(), List.of());
        }

        Long latestId = repo.findLatestVersionId(domain, level);
        if (latestId == null) {
            return new DeltaResponse(0, 0, null, List.of(), List.of(), List.of(), List.of());
        }
        Long fromId = (since > 0) ? repo.findVersionIdByScopeAndNumber(domain, level, since) : null;

        // coba muat dari cache
        String key  = kDelta(domain, level, since, latestVer);
        String json = safeRedisGet(key);

        // cache miss → query DB
        if (json == null) {
            if (fromId != null) {
                json = repo.findDeltaPayload(fromId, latestId); // delta since→latest
            }
            if (json == null) {
                json = repo.findDeltaPayload(latestId, latestId); // fallback snapshot latest→latest
                if (json != null) {
                    log.debug("content.download fallback to snapshot domain={} level={} since={}→{}", domain, level, since, latestVer);
                }
            }
            if (json != null) {
                safeRedisSet(key, json, Duration.ofHours(1));
            }
        }

        if (json == null) {
            // tidak ada payload sama sekali
            return new DeltaResponse(since, latestVer, null, List.of(), List.of(), List.of(), List.of());
        }

        Map<String, Object> map;
        try {
            map = om.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            // payload DB corrupt → jangan crash; kirim kosong & log
            log.warn("Invalid delta payload for domain={} level={} since={} (len={}): {}",
                    domain, level, since, json.length(), e.toString());
            return new DeltaResponse(since, latestVer, null, List.of(), List.of(), List.of(), List.of());
        }

        // checksum: kalau delta gagal/absen, pakai snapshot
        String checksum = (fromId != null)
                ? repo.findDeltaChecksum(fromId, latestId)
                : repo.findDeltaChecksum(latestId, latestId);
        if (checksum == null && fromId != null) {
            checksum = repo.findDeltaChecksum(latestId, latestId);
        }

        // Jika yang dipakai snapshot (fromId==null atau payload dari latest→latest),
        // fromVersion harus = latestVer (bukan 0).
        int fromVersionForResponse = (fromId != null) ? since : latestVer;

        return new DeltaResponse(
                fromVersionForResponse,
                latestVer,
                checksum,
                asList(map.get("decks")),
                asList(map.get("cards")),
                asList(map.get("passages")),
                asList(map.get("i18n"))
        );
    }

    // ==================
    // Helper functions
    // ==================

    private String safeRedisGet(String key) {
        try {
            return redis.opsForValue().get(key);
        } catch (DataAccessException ex) {
            log.debug("redis GET failed for key={} → {} (fallback DB)", key, ex.getMessage());
            return null;
        }
    }

    private void safeRedisSet(String key, String val, Duration ttl) {
        try {
            if (val != null) {
                redis.opsForValue().set(key, val, ttl);
            }
        } catch (DataAccessException ex) {
            log.debug("redis SET failed for key={} (ignore cache): {}", key, ex.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Object> asList(Object val) {
        if (val == null) return Collections.emptyList();
        if (val instanceof List<?> list) {
            // filter out nulls defensively
            return (List<Object>) list.stream().filter(Objects::nonNull).toList();
        }
        return Collections.emptyList();
    }
}
