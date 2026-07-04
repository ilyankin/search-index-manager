package com.izenkyt.searchindexmanager.index;

import com.izenkyt.searchindexmanager.common.DuplicateNameException;
import com.izenkyt.searchindexmanager.common.NotFoundException;
import com.izenkyt.searchindexmanager.index.api.dto.CreateIndexRequest;
import com.izenkyt.searchindexmanager.index.api.dto.FieldDefinition;
import com.izenkyt.searchindexmanager.index.api.dto.FieldType;
import com.izenkyt.searchindexmanager.index.api.dto.IndexResponse;
import com.izenkyt.searchindexmanager.index.api.dto.IndexVersionResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class IndexCatalogService {

    private final SearchIndexRepository searchIndexRepository;
    private final IndexVersionRepository indexVersionRepository;

    public IndexCatalogService(SearchIndexRepository searchIndexRepository,
                               IndexVersionRepository indexVersionRepository) {
        this.searchIndexRepository = searchIndexRepository;
        this.indexVersionRepository = indexVersionRepository;
    }

    @Transactional
    public IndexResponse create(CreateIndexRequest request) {
        if (searchIndexRepository.existsByName(request.name())) {
            throw new DuplicateNameException("Index with name '" + request.name() + "' already exists");
        }
        SearchIndex index = new SearchIndex(
                request.name(),
                request.description(),
                toFieldsMap(request.fields())
        );
        SearchIndex saved = searchIndexRepository.save(index);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public IndexResponse getById(UUID id) {
        return toResponse(requireIndex(id));
    }

    @Transactional(readOnly = true)
    public Page<IndexResponse> list(Pageable pageable) {
        return searchIndexRepository.findAll(pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public List<IndexVersionResponse> listVersions(UUID indexId) {
        requireIndexExists(indexId);
        return indexVersionRepository.findAllByIndexIdOrderByVersionDesc(indexId).stream()
                .map(this::toVersionResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public IndexVersionResponse getVersion(UUID indexId, int version) {
        return indexVersionRepository.findByIndexIdAndVersion(indexId, version)
                .map(this::toVersionResponse)
                .orElseGet(() -> {
                    requireIndexExists(indexId);
                    throw new NotFoundException(
                            "Version " + version + " not found for index " + indexId);
                });
    }

    private SearchIndex requireIndex(UUID id) {
        return searchIndexRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Index " + id + " not found"));
    }

    private void requireIndexExists(UUID id) {
        if (!searchIndexRepository.existsById(id)) {
            throw new NotFoundException("Index " + id + " not found");
        }
    }

    private IndexResponse toResponse(SearchIndex index) {
        return new IndexResponse(
                index.getId(),
                index.getName(),
                index.getDescription(),
                toFieldDefinitions(index.getFields()),
                index.getCreatedAt(),
                index.getUpdatedAt()
        );
    }

    private IndexVersionResponse toVersionResponse(IndexVersion version) {
        return new IndexVersionResponse(
                version.getId(),
                version.getVersion(),
                version.getStatus().name(),
                version.getDocCount(),
                version.getArtifactKey(),
                version.getArtifactSize(),
                version.getChecksum(),
                version.getErrorMessage(),
                version.getCreatedAt(),
                version.getUpdatedAt()
        );
    }

    private Map<String, String> toFieldsMap(List<FieldDefinition> fields) {
        Map<String, String> map = new LinkedHashMap<>();
        for (FieldDefinition f : fields) {
            map.put(f.name(), f.type().toStorageValue());
        }
        return map;
    }

    private List<FieldDefinition> toFieldDefinitions(Map<String, String> fields) {
        return fields.entrySet().stream()
                .map(e -> new FieldDefinition(e.getKey(), FieldType.fromStorageValue(e.getValue())))
                .toList();
    }
}