package com.nca.jlpt_companion.content.repo;

import com.nca.jlpt_companion.content.model.ContentVersionEntity;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ContentRepo extends CrudRepository<ContentVersionEntity, Long> {

    // --- versi by scope ---
    @Query(value = """
        SELECT COALESCE(MAX(version),0)
        FROM content_versions
        WHERE domain = ?1 AND ( ?2 IS NULL OR level::text = ?2 )
        """, nativeQuery = true)
    int findLatestVersionNumber(String domain, String level);

    @Query(value = """
        SELECT id
        FROM content_versions
        WHERE domain = ?1 AND ( ?2 IS NULL OR level::text = ?2 ) AND version = ?3
        LIMIT 1
        """, nativeQuery = true)
    Long findVersionIdByScopeAndNumber(String domain, String level, int version);

    @Query(value = """
        SELECT id
        FROM content_versions
        WHERE domain = ?1 AND ( ?2 IS NULL OR level::text = ?2 )
        ORDER BY version DESC, id DESC
        LIMIT 1
        """, nativeQuery = true)
    Long findLatestVersionId(String domain, String level);

    // --- delta payload/checksum/size by version ids ---
    @Query(value = "SELECT payload::text FROM content_deltas WHERE from_version = ?1 AND to_version = ?2", nativeQuery = true)
    String findDeltaPayload(long fromVersionId, long toVersionId);

    @Query(value = "SELECT checksum FROM content_deltas WHERE from_version = ?1 AND to_version = ?2", nativeQuery = true)
    String findDeltaChecksum(long fromVersionId, long toVersionId);

    @Query(value = "SELECT size_hint FROM content_deltas WHERE from_version = ?1 AND to_version = ?2", nativeQuery = true)
    Long findDeltaSize(long fromVersionId, long toVersionId);
}
