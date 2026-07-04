package com.izenkyt.searchindexmanager.indexbuild;

import com.izenkyt.searchindexmanager.common.NotFoundException;
import com.izenkyt.searchindexmanager.index.api.dto.IndexVersionResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@WebMvcTest(IndexBuildController.class)
class IndexBuildControllerWebMvcTest {

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private IndexBuildService buildService;

    private static final String NDJSON =
            "{\"title\":\"hello world\",\"count\":42}\n{\"title\":\"second doc\",\"count\":7}\n";

    private IndexVersionResponse createdResponse(UUID indexId, int version) {
        return new IndexVersionResponse(
                UUID.randomUUID(), version, "CREATED", null, null, null, null, null,
                Instant.parse("2026-07-04T09:00:00Z"),
                Instant.parse("2026-07-04T09:00:00Z")
        );
    }

    @Test
    void build_returns202WithLocationAndBody() {
        UUID indexId = UUID.randomUUID();
        IndexVersionResponse response = createdResponse(indexId, 1);
        given(buildService.startBuild(eq(indexId), any(java.io.InputStream.class)))
                .willReturn(response);

        assertThat(mvc.post().uri("/api/v1/indexes/" + indexId + "/build")
                .contentType(MediaType.parseMediaType("application/x-ndjson"))
                .content(NDJSON))
                .hasStatus(HttpStatus.ACCEPTED)
                .hasHeader("Location",
                        "http://localhost/api/v1/indexes/" + indexId + "/versions/1")
                .bodyJson().extractingPath("$.status").isEqualTo("CREATED");

        assertThat(mvc.post().uri("/api/v1/indexes/" + indexId + "/build")
                .contentType(MediaType.parseMediaType("application/x-ndjson"))
                .content(NDJSON))
                .hasStatus(HttpStatus.ACCEPTED)
                .bodyJson().extractingPath("$.version").isEqualTo(1);
    }

    @Test
    void build_returns404_whenIndexMissing() {
        UUID indexId = UUID.randomUUID();
        given(buildService.startBuild(eq(indexId), any(java.io.InputStream.class)))
                .willThrow(new NotFoundException("Index " + indexId + " not found"));

        assertThat(mvc.post().uri("/api/v1/indexes/" + indexId + "/build")
                .contentType(MediaType.parseMediaType("application/x-ndjson"))
                .content(NDJSON))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().extractingPath("$.detail").asString().contains(indexId.toString());
    }

    @Test
    void build_returns409_whenActiveBuildExists() {
        UUID indexId = UUID.randomUUID();
        given(buildService.startBuild(eq(indexId), any(java.io.InputStream.class)))
                .willThrow(new IndexBuildConflictException("Index " + indexId + " already has an active build"));

        assertThat(mvc.post().uri("/api/v1/indexes/" + indexId + "/build")
                .contentType(MediaType.parseMediaType("application/x-ndjson"))
                .content(NDJSON))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.detail").asString().contains("active build");
    }
}
