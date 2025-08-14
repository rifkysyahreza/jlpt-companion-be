package com.nca.jlpt_companion.content.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nca.jlpt_companion.content.dto.DeltaResponse;
import com.nca.jlpt_companion.content.dto.VersionResponse;
import com.nca.jlpt_companion.content.repo.ContentRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ContentService {

    private final ContentRepo repo;
    private final StringRedisTemplate redis;
    private final ObjectMapper om;

    private static String kLatest(String domain, String level){ return "content:%s:%s:latest".formatted(domain, level==null?"_":level); }
    private static String kDelta(String domain, String level, int since, int to){ return "content:%s:%s:delta:%d:%d".formatted(domain, level==null?"_":level, since, to); }

    public VersionResponse getLatestVersion(String domain, String level) {
        String cacheKey = kLatest(domain, level);
        String cached = redis.opsForValue().get(cacheKey);

        int latestVer;
        if (cached != null) {
            latestVer = Integer.parseInt(cached);
        } else {
            latestVer = repo.findLatestVersionNumber(domain, level);
            redis.opsForValue().set(cacheKey, Integer.toString(latestVer), Duration.ofMinutes(10));
        }

        if (latestVer <= 0) {
            return new VersionResponse(0, null, 0L);
        }

        Long latestId = repo.findLatestVersionId(domain, level);
        Long size = repo.findDeltaSize(latestId, latestId);
        String sum = repo.findDeltaChecksum(latestId, latestId);
        return new VersionResponse(latestVer, sum, size);
    }

    @SuppressWarnings("unchecked")
    public DeltaResponse getDelta(String domain, String level, int since) {
        int latestVer = repo.findLatestVersionNumber(domain, level);
        if (since >= latestVer) {
            return new DeltaResponse(since, latestVer, null, List.of(), List.of(), List.of(), List.of());
        }
        Long latestId = repo.findLatestVersionId(domain, level);
        Long fromId = (since > 0) ? repo.findVersionIdByScopeAndNumber(domain, level, since) : null;

        // try since->latest; if absent, fall back to snapshot latest->latest
        String key = kDelta(domain, level, since, latestVer);
        String json = redis.opsForValue().get(key);
        if (json == null) {
            if (fromId != null) {
                json = repo.findDeltaPayload(fromId, latestId);
            }
            if (json == null) {
                json = repo.findDeltaPayload(latestId, latestId);
            }
            if (json != null) redis.opsForValue().set(key, json, Duration.ofHours(1));
        }
        if (json == null) {
            // no payload at all (empty scope)
            return new DeltaResponse(since, latestVer, null, List.of(), List.of(), List.of(), List.of());
        }

        Map<String,Object> map;
        try { map = om.readValue(json, new TypeReference<>() {}); }
        catch (Exception e){ throw new RuntimeException("Invalid delta payload", e); }

        String checksum = (fromId != null) ? repo.findDeltaChecksum(fromId, latestId)
                : repo.findDeltaChecksum(latestId, latestId);

        int fromVersionForResponse = (fromId != null) ? since : latestVer;

        return new DeltaResponse(
                fromVersionForResponse, latestVer, checksum,
                (List<Object>) map.getOrDefault("decks", List.of()),
                (List<Object>) map.getOrDefault("cards", List.of()),
                (List<Object>) map.getOrDefault("passages", List.of()),
                (List<Object>) map.getOrDefault("i18n", List.of())
        );
    }
}
