package com.izenkyt.searchindexmanager.indexload;

import com.izenkyt.searchindexmanager.TestcontainersConfiguration;
import com.izenkyt.searchindexmanager.indexbuild.ArtifactPackager;
import com.izenkyt.searchindexmanager.indexload.LoadedIndexRegistry.LoadKey;
import com.izenkyt.searchindexmanager.indexload.LoadedIndexRegistry.LoadState;
import com.izenkyt.searchindexmanager.storage.ArtifactStorage;
import com.izenkyt.searchindexmanager.storage.MinioStorageProperties;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class IndexLoadPipelineIntegrationTest {

    @Container
    private static final MinIOContainer minio = new MinIOContainer(TestcontainersConfiguration.minioImage());

    private static final String BUCKET = "load-pipeline-test";

    @TempDir
    private Path tempDir;

    private final TarArchiveExtractor extractor = new TarArchiveExtractor();

    private MinioClient newClient() {
        return MinioClient.builder()
                .endpoint(minio.getS3URL())
                .credentials(minio.getUserName(), minio.getPassword())
                .build();
    }

    private ArtifactStorage newStorage() throws Exception {
        MinioClient client = newClient();
        if (!client.bucketExists(io.minio.BucketExistsArgs.builder().bucket(BUCKET).build())) {
            client.makeBucket(MakeBucketArgs.builder().bucket(BUCKET).build());
        }
        MinioStorageProperties props = new MinioStorageProperties(
                minio.getS3URL(), minio.getS3URL(), minio.getUserName(), minio.getPassword(),
                BUCKET, Duration.ofMinutes(5));
        return new ArtifactStorage(client, client, props);
    }

    private IndexLoadProperties newProperties() {
        return new IndexLoadProperties(tempDir.resolve("load-work").toString(),
                new IndexLoadProperties.Executor(1, 2, 50, "load-", Duration.ofSeconds(25)));
    }

    @Test
    void run_downloadsVerifiesExtractsAndOpensReader_marksLoaded() throws Exception {
        ArtifactStorage storage = newStorage();
        ArtifactPackager.ArtifactResult artifact = IndexLoadTestSupport.packageSampleIndex(tempDir, "load-pipeline-idx");
        UUID indexId = UUID.randomUUID();
        int version = 1;
        String artifactKey = indexId + "/" + version + "/index.tar.gz";
        storage.upload(artifactKey, artifact.path());

        LoadedIndexRegistry registry = new LoadedIndexRegistry();
        IndexLoadProperties properties = newProperties();
        IndexLoadPipeline pipeline = new IndexLoadPipeline(storage, extractor, registry, properties);
        LoadKey key = new LoadKey(indexId, version);
        registry.tryBeginLoad(key);

        pipeline.run(key, artifactKey, artifact.size(), artifact.checksum(), 2L);

        LoadedIndexRegistry.LoadedIndex entry = registry.list().stream()
                .filter(e -> e.key().equals(key)).findFirst().orElseThrow();
        assertThat(entry.state()).isEqualTo(LoadState.LOADED);
        assertThat(entry.numDocs()).isEqualTo(2L);
        assertThat(entry.sizeOnDiskBytes()).isPositive();
        assertThat(entry.error()).isNull();
        assertThat(properties.targetDir(indexId, version)).exists().isDirectory();
    }

    @Test
    void run_withCorruptedObjectInBucket_marksFailedAndLeavesNoTargetDir() throws Exception {
        ArtifactStorage storage = newStorage();
        ArtifactPackager.ArtifactResult artifact = IndexLoadTestSupport.packageSampleIndex(tempDir, "load-pipeline-idx");
        UUID indexId = UUID.randomUUID();
        int version = 1;
        String artifactKey = indexId + "/" + version + "/index.tar.gz";

        byte[] corrupted = "not a real tar.gz".getBytes();
        MinioClient client = newClient();
        client.putObject(PutObjectArgs.builder()
                .bucket(BUCKET)
                .object(artifactKey)
                .stream(new ByteArrayInputStream(corrupted), (long) corrupted.length, -1L)
                .build());

        LoadedIndexRegistry registry = new LoadedIndexRegistry();
        IndexLoadProperties properties = newProperties();
        IndexLoadPipeline pipeline = new IndexLoadPipeline(storage, extractor, registry, properties);
        LoadKey key = new LoadKey(indexId, version);
        registry.tryBeginLoad(key);

        pipeline.run(key, artifactKey, artifact.size(), artifact.checksum(), 2L);

        LoadedIndexRegistry.LoadedIndex entry = registry.list().stream()
                .filter(e -> e.key().equals(key)).findFirst().orElseThrow();
        assertThat(entry.state()).isEqualTo(LoadState.FAILED);
        assertThat(entry.error()).isNotBlank();
        assertThat(properties.targetDir(indexId, version)).doesNotExist();
    }
}
