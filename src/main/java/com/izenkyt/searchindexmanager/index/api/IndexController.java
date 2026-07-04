package com.izenkyt.searchindexmanager.index.api;

import com.izenkyt.searchindexmanager.index.IndexCatalogService;
import com.izenkyt.searchindexmanager.index.api.dto.CreateIndexRequest;
import com.izenkyt.searchindexmanager.index.api.dto.IndexResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

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
}
