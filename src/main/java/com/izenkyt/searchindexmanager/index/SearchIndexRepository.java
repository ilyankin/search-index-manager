package com.izenkyt.searchindexmanager.index;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SearchIndexRepository extends JpaRepository<SearchIndex, UUID> {

    Optional<SearchIndex> findByName(String name);

    boolean existsByName(String name);
}