package com.izenkyt.searchindexmanager;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;


@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    public static final String MINIO_IMAGE = "chainguard/minio:latest";
    public static final String KAFKA_IMAGE = "apache/kafka:4.3.1";

    public static DockerImageName minioImage() {
        return DockerImageName.parse(MINIO_IMAGE).asCompatibleSubstituteFor("minio/minio");
    }

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer("postgres:16")
                .withDatabaseName("searchindex")
                .withUsername("test")
                .withPassword("test");
    }

    @Bean
    MinIOContainer minioContainer() {
        return new MinIOContainer(minioImage());
    }

    @Bean
    @ServiceConnection
    KafkaContainer kafkaContainer() {
        return new KafkaContainer(KAFKA_IMAGE);
    }

    @Bean
    DynamicPropertyRegistrar minioPropertiesRegistrar(MinIOContainer minioContainer) {
        return registry -> {
            registry.add("search.index.storage.endpoint", minioContainer::getS3URL);
            registry.add("search.index.storage.public-endpoint", minioContainer::getS3URL);
            registry.add("search.index.storage.access-key", minioContainer::getUserName);
            registry.add("search.index.storage.secret-key", minioContainer::getPassword);
        };
    }

    @Bean
    DynamicPropertyRegistrar kafkaConsumerGroupRegistrar() {
        String groupId = "test-consumer-" + UUID.randomUUID();
        return registry -> registry.add("spring.kafka.consumer.group-id", () -> groupId);
    }
}
