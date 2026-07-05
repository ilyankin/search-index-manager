package com.izenkyt.searchindexmanager.indexload;

import com.izenkyt.searchindexmanager.TestcontainersConfiguration;
import com.izenkyt.searchindexmanager.indexbuild.IndexBuildTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ContextConfiguration(classes = TestcontainersConfiguration.class)
class IndexLoadApiIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @TempDir
    static Path buildWorkdir;

    @TempDir
    static Path loadWorkdir;

    @DynamicPropertySource
    static void registerDirs(DynamicPropertyRegistry registry) {
        IndexBuildTestSupport.registerWorkdir(registry, buildWorkdir);
        registry.add("search.index.load.dir", () -> loadWorkdir.toAbsolutePath().toString());
    }

    private String url(String path) {
        return IndexBuildTestSupport.url(port, path);
    }

    @Test
    void buildThenLoadThenUnload_indexBecomesLoadedAndCleansUpOnDelete() {
        UUID indexId = IndexBuildTestSupport.createIndex(restTemplate, port, "load-e2e-" + UUID.randomUUID());
        String ndjson =
                "{\"title\":\"hello world\",\"tag\":\"java\",\"count\":42}\n"
                        + "{\"title\":\"second doc\",\"tag\":\"python\",\"count\":7}\n";

        ResponseEntity<Map> build = IndexBuildTestSupport.postBuild(restTemplate, port, indexId, ndjson);
        assertThat(build.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        int version = ((Number) build.getBody().get("version")).intValue();

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            Map<String, Object> v = IndexBuildTestSupport.getVersion(restTemplate, port, indexId, version);
            assertThat(v.get("status")).isEqualTo("READY");
        });
        long docCount = ((Number) IndexBuildTestSupport.getVersion(restTemplate, port, indexId, version)
                .get("docCount")).longValue();

        ResponseEntity<Map> load = restTemplate.postForEntity(
                url("/indexes/" + indexId + "/versions/" + version + "/load"), null, Map.class);
        assertThat(load.getStatusCode()).isIn(HttpStatus.ACCEPTED, HttpStatus.OK);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(findLoaded(indexId, version).get("state")).isEqualTo("LOADED"));

        Map<String, Object> loaded = findLoaded(indexId, version);
        assertThat(((Number) loaded.get("numDocs")).longValue()).isEqualTo(docCount);
        assertThat(((Number) loaded.get("sizeOnDiskBytes")).longValue()).isPositive();
        Path targetDir = loadWorkdir.resolve(indexId.toString()).resolve(String.valueOf(version));
        assertThat(targetDir).exists().isDirectory();

        ResponseEntity<Void> delete = restTemplate.exchange(
                url("/indexes/" + indexId + "/versions/" + version + "/load"), HttpMethod.DELETE, null, Void.class);
        assertThat(delete.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(isLoaded(indexId, version)).isFalse();
        assertThat(Files.exists(targetDir)).isFalse();
    }

    @Test
    void load_returns404_whenVersionMissing() {
        UUID indexId = IndexBuildTestSupport.createIndex(restTemplate, port, "load-e2e-404-" + UUID.randomUUID());

        ResponseEntity<Map> load = restTemplate.postForEntity(
                url("/indexes/" + indexId + "/versions/1/load"), null, Map.class);

        assertThat(load.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private Map<String, Object> findLoaded(UUID indexId, int version) {
        ResponseEntity<Map[]> resp = restTemplate.getForEntity(url("/indexes/loaded"), Map[].class);
        return Arrays.stream(resp.getBody())
                .filter(m -> indexId.toString().equals(m.get("indexId")) && version == ((Number) m.get("version")).intValue())
                .findFirst()
                .orElseThrow();
    }

    private boolean isLoaded(UUID indexId, int version) {
        ResponseEntity<Map[]> resp = restTemplate.getForEntity(url("/indexes/loaded"), Map[].class);
        return Arrays.stream(resp.getBody())
                .anyMatch(m -> indexId.toString().equals(m.get("indexId")) && version == ((Number) m.get("version")).intValue());
    }
}
