package com.izenkyt.searchindexmanager;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;


@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    public static final String MINIO_IMAGE = "minio/minio:RELEASE.2024-08-17T01-24-54Z";

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
        return new MinIOContainer(MINIO_IMAGE);
    }

    @Bean
    DynamicPropertyRegistrar minioPropertiesRegistrar(MinIOContainer minioContainer) {
        return registry -> {
            registry.add("search.index.storage.endpoint", minioContainer::getS3URL);
            registry.add("search.index.storage.access-key", minioContainer::getUserName);
            registry.add("search.index.storage.secret-key", minioContainer::getPassword);
        };
    }
}
