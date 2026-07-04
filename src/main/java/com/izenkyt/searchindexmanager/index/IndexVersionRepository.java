package com.izenkyt.searchindexmanager.index;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IndexVersionRepository extends JpaRepository<IndexVersion, UUID> {

    List<IndexVersion> findAllByIndexIdOrderByVersionDesc(UUID indexId);

    Optional<IndexVersion> findByIndexIdAndVersion(UUID indexId, int version);

    @Query("select max(v.version) from IndexVersion v where v.index.id = :indexId")
    Optional<Integer> findMaxVersionByIndexId(UUID indexId);

    @Query("select case when count(v) > 0 then true else false end "
            + "from IndexVersion v where v.index.id = :indexId and v.status in "
            + "(com.izenkyt.searchindexmanager.index.IndexVersionStatus.CREATED, "
            + "com.izenkyt.searchindexmanager.index.IndexVersionStatus.BUILDING)")
    boolean hasActiveBuild(@Param("indexId") UUID indexId);
}