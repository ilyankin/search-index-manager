package com.izenkyt.searchindexmanager.index.api;

import com.izenkyt.searchindexmanager.index.IndexCatalogService;
import com.izenkyt.searchindexmanager.index.api.dto.ArtifactDownloadResponse;
import com.izenkyt.searchindexmanager.index.api.dto.CreateIndexRequest;
import com.izenkyt.searchindexmanager.index.api.dto.IndexResponse;
import com.izenkyt.searchindexmanager.index.api.dto.IndexVersionResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(path = "/indexes", version = "1")
public class IndexController {

    private final IndexCatalogService service;

    public IndexController(IndexCatalogService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<IndexResponse> create(@Valid @RequestBody CreateIndexRequest request) {
        IndexResponse created = service.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping
    public Page<IndexResponse> list(Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/{id}")
    public IndexResponse get(@PathVariable UUID id) {
        return service.getById(id);
    }

    @GetMapping("/{id}/versions")
    public List<IndexVersionResponse> listVersions(@PathVariable UUID id) {
        return service.listVersions(id);
    }

    @GetMapping("/{id}/versions/{version}")
    public IndexVersionResponse getVersion(@PathVariable UUID id, @PathVariable int version) {
        return service.getVersion(id, version);
    }

    @GetMapping("/{id}/versions/{version}/artifact")
    public ArtifactDownloadResponse getArtifact(@PathVariable UUID id, @PathVariable int version) {
        return service.getArtifactDownloadUrl(id, version);
    }
}