package com.izenkyt.searchindexmanager.storage;

import com.izenkyt.searchindexmanager.TestcontainersConfiguration;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class ArtifactStorageIntegrationTest {

    @Container
    private static final MinIOContainer minio = new MinIOContainer(TestcontainersConfiguration.MINIO_IMAGE);

    @TempDir
    Path tempDir;

    private MinioClient newClient(String endpoint, String accessKey, String secretKey) {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }

    private ArtifactStorage newStorage(String bucket) {
        MinioStorageProperties props = new MinioStorageProperties();
        props.setEndpoint(minio.getS3URL());
        props.setAccessKey(minio.getUserName());
        props.setSecretKey(minio.getPassword());
        props.setBucket(bucket);
        props.setPresignTtl(Duration.ofMinutes(5));
        MinioClient client = newClient(minio.getS3URL(), minio.getUserName(), minio.getPassword());
        return new ArtifactStorage(client, client, props);
    }

    @Test
    void upload_storesObjectDownloadableViaPresignedUrlWithMatchingContentAndSize() throws Exception {
        ArtifactStorage storage = newStorage("artifacts-test-ok");
        MinioClient admin = newClient(minio.getS3URL(), minio.getUserName(), minio.getPassword());
        admin.makeBucket(MakeBucketArgs.builder().bucket("artifacts-test-ok").build());

        Path file = tempDir.resolve("index.tar.gz");
        byte[] payload = new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
        Files.write(file, payload);

        String key = "idx-1/1/index.tar.gz";
        storage.upload(key, file);

        ArtifactStorage.PresignedUrl presigned = storage.download(key);
        assertThat(presigned.url()).startsWith("http");
        assertThat(presigned.expiresAt()).isAfter(java.time.Instant.now());

        HttpResponse<byte[]> resp = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(presigned.url())).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertThat(resp.statusCode()).isEqualTo(200);
        assertThat(resp.body()).isEqualTo(payload);
        assertThat(resp.body().length).isEqualTo(payload.length);
    }

    @Test
    void upload_withUnreachableEndpoint_throwsReadableException() throws Exception {
        MinioStorageProperties props = new MinioStorageProperties();
        props.setEndpoint("http://localhost:1");
        props.setAccessKey("minioadmin");
        props.setSecretKey("minioadmin");
        props.setBucket("missing-bucket");
        MinioClient client = newClient("http://localhost:1", "minioadmin", "minioadmin");
        ArtifactStorage storage = new ArtifactStorage(client, client, props);

        Path file = tempDir.resolve("index.tar.gz");
        Files.write(file, new byte[]{1, 2, 3});

        assertThatThrownBy(() -> storage.upload("k", file))
                .isInstanceOf(ArtifactStorageException.class)
                .hasMessageContaining("Failed to upload artifact");
    }
}
