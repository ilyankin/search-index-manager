package com.izenkyt.searchindexmanager.index;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IndexVersionRepository extends JpaRepository<IndexVersion, UUID> {

    List<IndexVersion> findAllByIndexIdOrderByVersionDesc(UUID indexId);

    @Query("select max(v.version) from IndexVersion v where v.index.id = :indexId")
    Optional<Integer> findMaxVersionByIndexId(UUID indexId);
}