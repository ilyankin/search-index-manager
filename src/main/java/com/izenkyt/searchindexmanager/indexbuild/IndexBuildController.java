package com.izenkyt.searchindexmanager.indexbuild;

import com.izenkyt.searchindexmanager.index.api.dto.IndexVersionResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping(path = "/indexes", version = "1")
public class IndexBuildController {

    private static final Logger log = LoggerFactory.getLogger(IndexBuildController.class);

    private final IndexBuildService buildService;

    public IndexBuildController(IndexBuildService buildService) {
        this.buildService = buildService;
    }

    @PostMapping("/{id}/build")
    public ResponseEntity<IndexVersionResponse> build(@PathVariable UUID id,
                                                      HttpServletRequest request) throws IOException {
        log.debug("Received build request for index {}", id);
        try (InputStream ndjson = request.getInputStream()) {
            IndexVersionResponse response = buildService.startBuild(id, ndjson);
            URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                    .pathSegment("api", "v1", "indexes", id.toString(),
                            "versions", String.valueOf(response.version()))
                    .build()
                    .toUri();
            log.info("Accepted build for index {}: version {}", id, response.version());
            return ResponseEntity.accepted().location(location).body(response);
        }
    }
}
