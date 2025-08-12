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

    private static final String KEY_LATEST = "content:latest_version";
    private static String dkey(long f, long t) { return "content:delta:%d:%d".formatted(f, t); }

    public VersionResponse getLatestVersion() {
        String cached = redis.opsForValue().get(KEY_LATEST);
        long latest;
        if (cached != null) {
            latest = Long.parseLong(cached);
        } else {
            latest = repo.findLatestVersion();
            redis.opsForValue().set(KEY_LATEST, Long.toString(latest), Duration.ofMinutes(10));
        }
        Long size = (latest > 0) ? repo.findDeltaSize(latest, latest) : 0L;
        String checksum = (latest > 0) ? repo.findDeltaChecksum(latest, latest) : null;
        return new VersionResponse(latest, checksum, size);
    }

    @SuppressWarnings("unchecked")
    public DeltaResponse getDelta(long since) {
        long latest = repo.findLatestVersion();
        if (since >= latest) {
            return new DeltaResponse(since, latest, null, List.of(), List.of(), List.of(), List.of());
        }

        String json = redis.opsForValue().get(dkey(since, latest));
        if (json == null) {
            json = repo.findDeltaPayload(since, latest);
            if (json == null) {
                // fallback to snapshot latest->latest
                json = repo.findDeltaPayload(latest, latest);
            }
            if (json != null) {
                redis.opsForValue().set(dkey(since, latest), json, Duration.ofHours(1));
            }
        }
        if (json == null) throw new RuntimeException("Delta payload not found");

        Map<String, Object> map;
        try {
            map = om.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            throw new RuntimeException("Invalid delta payload", e);
        }
        String checksum = repo.findDeltaChecksum(since, latest);
        if (checksum == null) checksum = repo.findDeltaChecksum(latest, latest);

        return new DeltaResponse(
                since, latest, checksum,
                (List<Object>) map.getOrDefault("decks", List.of()),
                (List<Object>) map.getOrDefault("cards", List.of()),
                (List<Object>) map.getOrDefault("passages", List.of()),
                (List<Object>) map.getOrDefault("i18n", List.of())
        );
    }
}
