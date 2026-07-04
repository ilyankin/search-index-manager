package com.izenkyt.searchindexmanager.index;

import com.izenkyt.searchindexmanager.common.DuplicateNameException;
import com.izenkyt.searchindexmanager.common.NotFoundException;
import com.izenkyt.searchindexmanager.index.api.dto.CreateIndexRequest;
import com.izenkyt.searchindexmanager.index.api.dto.FieldDefinition;
import com.izenkyt.searchindexmanager.index.api.dto.FieldType;
import com.izenkyt.searchindexmanager.index.api.dto.IndexResponse;
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

    private SearchIndex requireIndex(UUID id) {
        return searchIndexRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Index " + id + " not found"));
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
