package com.izenkyt.searchindexmanager.storage;

import io.minio.MinioClient;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArtifactStorageTest {

    private final MinioClient client = MinioClient.builder()
            .endpoint("http://localhost:9000")
            .credentials("minioadmin", "minioadmin")
            .build();

    @Test
    void constructor_throws_whenPresignTtlExceedsSevenDays() {
        MinioStorageProperties properties = new MinioStorageProperties();
        properties.setPresignTtl(Duration.ofDays(30));

        assertThatThrownBy(() -> new ArtifactStorage(client, properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("presign-ttl");
    }

    @Test
    void constructor_throws_whenPresignTtlIsZeroOrNegative() {
        MinioStorageProperties properties = new MinioStorageProperties();
        properties.setPresignTtl(Duration.ZERO);

        assertThatThrownBy(() -> new ArtifactStorage(client, properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("presign-ttl");
    }
}
