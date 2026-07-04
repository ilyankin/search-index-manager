package com.izenkyt.searchindexmanager.index.api;

import com.izenkyt.searchindexmanager.common.DuplicateNameException;
import com.izenkyt.searchindexmanager.common.NotFoundException;
import com.izenkyt.searchindexmanager.index.IndexCatalogService;
import com.izenkyt.searchindexmanager.index.api.dto.CreateIndexRequest;
import com.izenkyt.searchindexmanager.index.api.dto.FieldDefinition;
import com.izenkyt.searchindexmanager.index.api.dto.FieldType;
import com.izenkyt.searchindexmanager.index.api.dto.IndexResponse;
import com.izenkyt.searchindexmanager.index.api.dto.IndexVersionResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@WebMvcTest(IndexController.class)
class IndexControllerWebMvcTest {

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private IndexCatalogService service;

    private static final String VALID_BODY = """
            {
              "name": "idx",
              "description": "d",
              "fields": [
                {"name": "title", "type": "text"}
              ]
            }
            """;

    private IndexResponse sampleResponse(UUID id) {
        return new IndexResponse(
                id, "idx", "desc",
                List.of(new FieldDefinition("title", FieldType.TEXT)),
                Instant.parse("2026-07-04T09:00:00Z"),
                Instant.parse("2026-07-04T09:00:00Z")
        );
    }

    private IndexVersionResponse sampleVersion(int version, String status) {
        return new IndexVersionResponse(
                UUID.randomUUID(), version, status, null, null, null, null, null,
                Instant.parse("2026-07-04T09:00:00Z"),
                Instant.parse("2026-07-04T09:00:00Z")
        );
    }

    @Test
    void create_returns201WithLocationAndBody() {
        UUID id = UUID.randomUUID();
        given(service.create(any(CreateIndexRequest.class))).willReturn(sampleResponse(id));

        assertThat(mvc.post().uri("/api/v1/indexes")
                .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .hasStatus(HttpStatus.CREATED)
                .hasHeader("Location", "http://localhost/api/v1/indexes/" + id)
                .bodyJson().extractingPath("$.name").isEqualTo("idx");
    }

    @Test
    void create_returns400_whenNameBlank() {
        String body = """
                {
                  "name": "",
                  "description": "d",
                  "fields": [
                    {"name": "title", "type": "text"}
                  ]
                }
                """;

        assertThat(mvc.post().uri("/api/v1/indexes")
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.errors.name").isNotNull();
    }

    @Test
    void create_returns400_whenNameInvalidPattern() {
        String body = """
                {
                  "name": "Bad Name",
                  "description": "d",
                  "fields": [
                    {"name": "title", "type": "text"}
                  ]
                }
                """;

        assertThat(mvc.post().uri("/api/v1/indexes")
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.errors.name").asString()
                .doesNotContain("Bad Name")
                .contains("a-z0-9_-");
    }

    @Test
    void create_returns400_whenFieldsEmpty() {
        String body = """
                {
                  "name": "idx",
                  "description": "d",
                  "fields": []
                }
                """;

        assertThat(mvc.post().uri("/api/v1/indexes")
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.errors.fields").isNotNull();
    }

    @Test
    void create_returns400_whenFieldNamesDuplicated() {
        String body = """
                {
                  "name": "idx",
                  "description": "d",
                  "fields": [
                    {"name": "title", "type": "text"},
                    {"name": "title", "type": "long"}
                  ]
                }
                """;

        assertThat(mvc.post().uri("/api/v1/indexes")
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.errors.fields").isNotNull();
    }

    @Test
    void create_returns400_whenFieldTypeInvalid() {
        String body = """
                {
                  "name": "idx",
                  "description": "d",
                  "fields": [
                    {"name": "t", "type": "bogus"}
                  ]
                }
                """;

        assertThat(mvc.post().uri("/api/v1/indexes")
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void create_returns409_whenDuplicateName() {
        given(service.create(any(CreateIndexRequest.class)))
                .willThrow(new DuplicateNameException("Index with name 'idx' already exists"));

        assertThat(mvc.post().uri("/api/v1/indexes")
                .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.detail").asString().contains("already exists");
    }

    @Test
    void create_returns409_whenConcurrentInsertViolatesUniqueConstraint() {
        given(service.create(any(CreateIndexRequest.class)))
                .willThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint"));

        assertThat(mvc.post().uri("/api/v1/indexes")
                .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .hasStatus(HttpStatus.CONFLICT);
    }

    @Test
    void create_returns400WithProblemDetail_whenBodyIsMalformedJson() {
        assertThat(mvc.post().uri("/api/v1/indexes")
                .contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.title").isNotNull();
    }

    @Test
    void getVersion_returns400WithProblemDetail_whenVersionIsNotNumeric() {
        UUID id = UUID.randomUUID();

        assertThat(mvc.get().uri("/api/v1/indexes/" + id + "/versions/abc"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.title").isNotNull();
    }

    @Test
    void get_returns200_whenFound() {
        UUID id = UUID.randomUUID();
        given(service.getById(id)).willReturn(sampleResponse(id));

        assertThat(mvc.get().uri("/api/v1/indexes/" + id))
                .hasStatusOk()
                .bodyJson().extractingPath("$.name").isEqualTo("idx");
    }

    @Test
    void get_returns404_whenMissing() {
        UUID id = UUID.randomUUID();
        given(service.getById(id)).willThrow(new NotFoundException("Index " + id + " not found"));

        assertThat(mvc.get().uri("/api/v1/indexes/" + id))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().extractingPath("$.detail").asString().contains(id.toString());
    }

    @Test
    void list_returns200WithPage() {
        UUID id = UUID.randomUUID();
        given(service.list(any())).willReturn(
                new PageImpl<>(List.of(sampleResponse(id)), PageRequest.of(0, 10), 1)
        );

        assertThat(mvc.get().uri("/api/v1/indexes?page=0&size=10"))
                .hasStatusOk()
                .bodyJson().extractingPath("$.content[0].name").isEqualTo("idx");
    }

    @Test
    void listVersions_returns200_whenIndexExists() {
        UUID id = UUID.randomUUID();
        given(service.listVersions(id)).willReturn(List.of(
                sampleVersion(2, "BUILT"),
                sampleVersion(1, "CREATED")
        ));

        assertThat(mvc.get().uri("/api/v1/indexes/" + id + "/versions"))
                .hasStatusOk()
                .bodyJson().extractingPath("$[0].version").isEqualTo(2);
    }

    @Test
    void listVersions_returns404_whenIndexMissing() {
        UUID id = UUID.randomUUID();
        given(service.listVersions(id)).willThrow(new NotFoundException("Index " + id + " not found"));

        assertThat(mvc.get().uri("/api/v1/indexes/" + id + "/versions"))
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void getVersion_returns200_whenFound() {
        UUID id = UUID.randomUUID();
        given(service.getVersion(eq(id), eq(1))).willReturn(sampleVersion(1, "CREATED"));

        assertThat(mvc.get().uri("/api/v1/indexes/" + id + "/versions/1"))
                .hasStatusOk()
                .bodyJson().extractingPath("$.version").isEqualTo(1);
    }

    @Test
    void getVersion_returns404_whenVersionMissing() {
        UUID id = UUID.randomUUID();
        given(service.getVersion(eq(id), eq(1))).willThrow(new NotFoundException("Version 1 not found"));

        assertThat(mvc.get().uri("/api/v1/indexes/" + id + "/versions/1"))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().extractingPath("$.detail").asString().contains("Version 1");
    }
}