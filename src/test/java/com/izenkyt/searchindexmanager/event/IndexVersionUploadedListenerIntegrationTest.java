package com.izenkyt.searchindexmanager.event;

import com.izenkyt.searchindexmanager.TestcontainersConfiguration;
import com.izenkyt.searchindexmanager.indexbuild.IndexBuildTestSupport;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ContextConfiguration(classes = TestcontainersConfiguration.class)
class IndexVersionUploadedListenerIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private IndexVersionEventPublisher publisher;

    @Autowired
    private IndexEventsProperties eventsProperties;

    @Autowired
    private ConsumerFactory<?, ?> consumerFactory;

    @Autowired
    private KafkaTemplate<Object, Object> kafkaTemplate;

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

    private Map<String, Object> getVersion(UUID indexId, int version) {
        return IndexBuildTestSupport.getVersion(restTemplate, port, indexId, version);
    }

    @Test
    void build_eventFlipsStatusToReady() {
        UUID indexId = createIndex("consumer-ready-" + UUID.randomUUID());
        String ndjson = "{\"title\":\"hello\",\"tag\":\"java\",\"count\":1}\n";

        ResponseEntity<Map> resp = postBuild(indexId, ndjson);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        int version = ((Number) resp.getBody().get("version")).intValue();

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            Map<String, Object> v = getVersion(indexId, version);
            assertThat(v.get("status")).isEqualTo("READY");
        });

        Map<String, Object> v = getVersion(indexId, version);
        assertThat(v.get("artifactKey")).isEqualTo(IndexBuildTestSupport.artifactKey(indexId, version));
    }

    @Test
    void duplicateEvent_isIdempotent_statusStaysReady() {
        UUID indexId = createIndex("consumer-dup-" + UUID.randomUUID());
        String ndjson = "{\"title\":\"hello\",\"tag\":\"java\",\"count\":1}\n";

        ResponseEntity<Map> resp = postBuild(indexId, ndjson);
        int version = ((Number) resp.getBody().get("version")).intValue();
        UUID versionId = UUID.fromString((String) resp.getBody().get("id"));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertThat(getVersion(indexId, version).get("status")).isEqualTo("READY");
        });

        IndexVersionUploadedEvent duplicate = new IndexVersionUploadedEvent(
                versionId, indexId, version, IndexBuildTestSupport.artifactKey(indexId, version),
                99L, "deadbeef", Instant.now());
        publisher.publish(duplicate);

        await().pollDelay(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(getVersion(indexId, version).get("status")).isEqualTo("READY");
        });
    }

    @Test
    void eventForNonExistentVersion_goesToDltWithoutRetries() {
        UUID phantomVersionId = UUID.randomUUID();
        UUID phantomIndexId = UUID.randomUUID();
        IndexVersionUploadedEvent event = new IndexVersionUploadedEvent(
                phantomVersionId, phantomIndexId, 1, "phantom/1/index.tar.gz",
                1L, "cafe", Instant.now());

        publisher.publish(event);

        try (Consumer<String, byte[]> dltConsumer = newDltConsumer()) {
            await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                    assertThat(dltContains(dltConsumer, phantomVersionId.toString())).isTrue());
        }
    }

    @Test
    void poisonPill_goesToDlt() {
        String marker = "poison-pill-" + UUID.randomUUID();
        kafkaTemplate.send(eventsProperties.topic(), marker, marker);

        try (Consumer<String, byte[]> dltConsumer = newDltConsumer()) {
            await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                    assertThat(dltContains(dltConsumer, marker)).isTrue());
        }
    }

    private boolean dltContains(Consumer<String, byte[]> consumer, String needle) {
        var records = consumer.poll(Duration.ofMillis(500));
        for (ConsumerRecord<String, byte[]> r : records) {
            if (r.value() != null && new String(r.value()).contains(needle)) {
                return true;
            }
            if (r.key() != null && r.key().contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private Consumer<String, byte[]> newDltConsumer() {
        Object bootstrapServers = consumerFactory.getConfigurationProperties().get(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG);
        // "[localhost:9092]" -> "localhost:9092" (Kafka bootstrap.servers format)
        String bootstrapServersProp = bootstrapServers instanceof List<?> list
                ? list.stream().map(String::valueOf).collect(Collectors.joining(","))
                : String.valueOf(bootstrapServers);
        Map<String, Object> props = KafkaTestUtils.consumerProps(
                bootstrapServersProp, "dlt-raw-" + UUID.randomUUID(), false);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
        Consumer<String, byte[]> consumer = new DefaultKafkaConsumerFactory<String, byte[]>(props).createConsumer();
        consumer.subscribe(List.of(eventsProperties.deadLetterTopic()));
        return consumer;
    }
}
