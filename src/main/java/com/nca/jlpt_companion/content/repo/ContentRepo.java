package com.nca.jlpt_companion.content.repo;

import com.nca.jlpt_companion.content.model.ContentVersionEntity;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ContentRepo extends CrudRepository<ContentVersionEntity, Long> {

    @Query(value = "SELECT COALESCE(MAX(id),0) FROM content_versions", nativeQuery = true)
    long findLatestVersion();

    @Query(value = "SELECT payload::text FROM content_deltas WHERE from_version = ?1 AND to_version = ?2", nativeQuery = true)
    String findDeltaPayload(long from, long to);

    @Query(value = "SELECT checksum FROM content_deltas WHERE from_version = ?1 AND to_version = ?2", nativeQuery = true)
    String findDeltaChecksum(long from, long to);

    @Query(value = "SELECT size_hint FROM content_deltas WHERE from_version = ?1 AND to_version = ?2", nativeQuery = true)
    Long findDeltaSize(long from, long to);
}
