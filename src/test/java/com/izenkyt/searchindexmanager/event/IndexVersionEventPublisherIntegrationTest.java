package com.izenkyt.searchindexmanager.event;

import com.izenkyt.searchindexmanager.TestcontainersConfiguration;
import com.izenkyt.searchindexmanager.indexbuild.IndexBuildTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ContextConfiguration(classes = {TestcontainersConfiguration.class, IndexVersionEventPublisherIntegrationTest.TestConsumerConfig.class})
class IndexVersionEventPublisherIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private TestEventConsumer testEventConsumer;

    @TempDir
    static Path workdir;

    @DynamicPropertySource
    static void registerWorkdir(DynamicPropertyRegistry registry) {
        IndexBuildTestSupport.registerWorkdir(registry, workdir);
    }

    private UUID createIndex(String name) {
        return IndexBuildTestSupport.createIndex(restTemplate, port, name);
    }

    private ResponseEntity<Map> postBuild(UUID indexId, String ndjson) {
        return IndexBuildTestSupport.postBuild(restTemplate, port, indexId, ndjson);
    }

    @AfterEach
    void clearCapturedEvents() {
        testEventConsumer.events.clear();
    }

    @Test
    void build_publishesEventWithCorrectPayload() {
        UUID indexId = createIndex("publisher-it-" + UUID.randomUUID());
        String ndjson =
                "{\"title\":\"hello world\",\"tag\":\"java\",\"count\":42}\n"
                        + "{\"title\":\"second doc\",\"tag\":\"python\",\"count\":7}\n";

        ResponseEntity<Map> resp = postBuild(indexId, ndjson);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        int version = ((Number) resp.getBody().get("version")).intValue();
        UUID versionId = UUID.fromString((String) resp.getBody().get("id"));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertThat(testEventConsumer.events.stream().filter(e -> e.versionId().equals(versionId)).findFirst())
                    .isPresent();
        });

        IndexVersionUploadedEvent event = testEventConsumer.events.stream()
                .filter(e -> e.versionId().equals(versionId)).findFirst().orElseThrow();
        assertThat(event.versionId()).isEqualTo(versionId);
        assertThat(event.indexId()).isEqualTo(indexId);
        assertThat(event.versionNumber()).isEqualTo(version);
        assertThat(event.artifactKey()).isEqualTo(IndexBuildTestSupport.artifactKey(indexId, version));
        assertThat(event.artifactSize()).isPositive();
        assertThat(event.checksum()).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(event.occurredAt()).isNotNull();
    }

    @TestConfiguration
    static class TestConsumerConfig {

        @Bean
        TestEventConsumer testEventConsumer() {
            return new TestEventConsumer();
        }
    }

    static class TestEventConsumer {

        final List<IndexVersionUploadedEvent> events = new CopyOnWriteArrayList<>();

        @KafkaListener(topics = "${search.index.events.topic}", groupId = "publisher-it")
        void on(IndexVersionUploadedEvent event) {
            events.add(event);
        }
    }
}
