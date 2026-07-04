package com.izenkyt.searchindexmanager.indexbuild;

import com.izenkyt.searchindexmanager.TestcontainersConfiguration;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ContextConfiguration(classes = TestcontainersConfiguration.class)
class IndexBuildPipelineIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private MinioClient minioClient;

    @Value("${search.index.storage.bucket}")
    private String bucket;

    @TempDir
    static Path workdir;

    @DynamicPropertySource
    static void registerWorkdir(DynamicPropertyRegistry registry) {
        registry.add("search.index.build.workdir", () -> workdir.toAbsolutePath().toString());
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

    @SuppressWarnings("unchecked")
    @Test
    void postBuild_resultsInUploadedVersionWithS3Artifact() throws Exception {
        UUID indexId = createIndex("build-ok-" + UUID.randomUUID());
        String ndjson =
                "{\"title\":\"hello world\",\"tag\":\"java\",\"count\":42}\n"
                        + "{\"title\":\"second doc\",\"tag\":\"python\",\"count\":7}\n";

        ResponseEntity<Map> resp = postBuild(indexId, ndjson);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(resp.getHeaders().getLocation()).isNotNull();
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().get("status")).isEqualTo("CREATED");
        int version = ((Number) resp.getBody().get("version")).intValue();

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            Map<String, Object> v = getVersion(indexId, version);
            assertThat(v.get("status")).isEqualTo("UPLOADED");
        });

        Map<String, Object> v = getVersion(indexId, version);
        assertThat(((Number) v.get("docCount")).longValue()).isEqualTo(2L);
        assertThat(((Number) v.get("artifactSize")).longValue()).isPositive();
        assertThat((String) v.get("checksum")).hasSize(64).matches("[0-9a-f]{64}");
        String artifactKey = (String) v.get("artifactKey");
        assertThat(artifactKey).isEqualTo(indexId + "/" + version + "/index.tar.gz");
        assertThat(objectExists(artifactKey)).isTrue();
        assertThat(objectSize(artifactKey))
                .isEqualTo(((Number) v.get("artifactSize")).longValue());
    }

    @SuppressWarnings("unchecked")
    @Test
    void postBuild_withBadNdjson_resultsInFailedWithErrorMessage() {
        UUID indexId = createIndex("build-fail-" + UUID.randomUUID());
        String ndjson =
                "{\"title\":\"ok\",\"tag\":\"a\",\"count\":1}\n"
                        + "{\"title\":\"bad\",\"bogus\":\"x\",\"count\":2}\n";

        ResponseEntity<Map> resp = postBuild(indexId, ndjson);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        int version = ((Number) resp.getBody().get("version")).intValue();

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            Map<String, Object> v = getVersion(indexId, version);
            assertThat(v.get("status")).isEqualTo("FAILED");
        });

        Map<String, Object> v = getVersion(indexId, version);
        assertThat((String) v.get("errorMessage")).contains("Line 2").contains("bogus");
        assertThat(v.get("artifactKey")).isNull();
        assertThat(v.get("artifactSize")).isNull();
        assertThat(v.get("checksum")).isNull();
    }

    @SuppressWarnings("unchecked")
    @Test
    void secondBuildWhileActiveReturns409() {
        UUID indexId = createIndex("build-conflict-" + UUID.randomUUID());
        String ndjson =
                "{\"title\":\"first\",\"tag\":\"a\",\"count\":1}\n"
                        + "{\"title\":\"second\",\"tag\":\"b\",\"count\":2}\n";

        ResponseEntity<Map> first = postBuild(indexId, ndjson);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        int version = ((Number) first.getBody().get("version")).intValue();

        ResponseEntity<Map> second = postBuild(indexId, ndjson);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            Map<String, Object> v = getVersion(indexId, version);
            assertThat(v.get("status")).isIn("CREATED", "BUILDING", "BUILT", "UPLOADED");
        });
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            Map<String, Object> v = getVersion(indexId, version);
            assertThat(v.get("status")).isEqualTo("UPLOADED");
        });
    }

    @SuppressWarnings("unchecked")
    @Test
    void postBuild_returns404_whenIndexMissing() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/x-ndjson"));
        ResponseEntity<Map> resp = restTemplate.postForEntity(
                url("/indexes/" + UUID.randomUUID() + "/build"),
                new HttpEntity<>("{\"title\":\"x\"}\n", headers), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private boolean objectExists(String key) {
        try {
            minioClient.statObject(StatObjectArgs.builder().bucket(bucket).object(key).build());
            return true;
        } catch (ErrorResponseException e) {
            return false;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private long objectSize(String key) throws Exception {
        return minioClient.statObject(StatObjectArgs.builder().bucket(bucket).object(key).build()).size();
    }
}
