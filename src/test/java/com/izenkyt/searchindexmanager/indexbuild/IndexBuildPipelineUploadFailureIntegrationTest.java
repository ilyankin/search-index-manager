package com.izenkyt.searchindexmanager.indexbuild;

import com.izenkyt.searchindexmanager.TestcontainersConfiguration;
import com.izenkyt.searchindexmanager.storage.ArtifactStorage;
import com.izenkyt.searchindexmanager.storage.ArtifactStorageException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ContextConfiguration(classes = TestcontainersConfiguration.class)
class IndexBuildPipelineUploadFailureIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @MockitoBean
    private ArtifactStorage artifactStorage;

    @TempDir
    static Path workdir;

    @DynamicPropertySource
    static void registerWorkdir(DynamicPropertyRegistry registry) {
        IndexBuildTestSupport.registerWorkdir(registry, workdir);
    }

    @BeforeEach
    void stubUploadFailure() {
        doThrow(new ArtifactStorageException("minio down"))
                .when(artifactStorage).upload(any(String.class), any(Path.class));
    }

    private String url(String path) {
        return IndexBuildTestSupport.url(port, path);
    }

    private UUID createIndex(String name) {
        return IndexBuildTestSupport.createIndex(restTemplate, port, name);
    }

    private ResponseEntity<Map> postBuild(UUID indexId, String ndjson) {
        return IndexBuildTestSupport.postBuild(restTemplate, port, indexId, ndjson);
    }

    private Map<String, Object> getVersion(UUID indexId, int version) {
        return IndexBuildTestSupport.getVersion(restTemplate, port, indexId, version);
    }

    @Test
    void uploadFailure_marksVersionFailedWithErrorMessage() {
        UUID indexId = createIndex("build-upload-fail-" + UUID.randomUUID());
        String ndjson =
                "{\"title\":\"hello world\",\"tag\":\"java\",\"count\":42}\n"
                        + "{\"title\":\"second doc\",\"tag\":\"python\",\"count\":7}\n";

        ResponseEntity<Map> resp = postBuild(indexId, ndjson);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        int version = ((Number) resp.getBody().get("version")).intValue();

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            Map<String, Object> v = getVersion(indexId, version);
            assertThat(v.get("status")).isEqualTo("FAILED");
        });

        Map<String, Object> v = getVersion(indexId, version);
        assertThat((String) v.get("errorMessage")).contains("minio down");
        assertThat(v.get("artifactKey")).isNull();
        assertThat(v.get("docCount")).isNull();
        assertThat(v.get("artifactSize")).isNull();
        assertThat(v.get("checksum")).isNull();
    }
}
