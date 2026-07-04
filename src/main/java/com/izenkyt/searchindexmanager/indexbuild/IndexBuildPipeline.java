package com.izenkyt.searchindexmanager.indexbuild;

import com.izenkyt.searchindexmanager.index.IndexVersion;
import com.izenkyt.searchindexmanager.index.SearchIndex;
import com.izenkyt.searchindexmanager.storage.ArtifactStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.FileSystemUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;

@Component
public class IndexBuildPipeline {

    private static final Logger log = LoggerFactory.getLogger(IndexBuildPipeline.class);

    private final IndexBuildStateStore stateStore;
    private final LuceneIndexBuilder indexBuilder;
    private final ArtifactPackager packager;
    private final ArtifactStorage storage;
    private final IndexBuildProperties properties;

    public IndexBuildPipeline(IndexBuildStateStore stateStore,
                              LuceneIndexBuilder indexBuilder,
                              ArtifactPackager packager,
                              ArtifactStorage storage,
                              IndexBuildProperties properties) {
        this.stateStore = stateStore;
        this.indexBuilder = indexBuilder;
        this.packager = packager;
        this.storage = storage;
        this.properties = properties;
    }

    @Async("buildExecutor")
    public void run(UUID versionId, Path ndjsonFile) {
        MDC.put("versionId", versionId.toString());
        try {
            doRun(versionId, ndjsonFile);
        } catch (Exception e) {
            failSafely(versionId, e);
        } finally {
            deleteQuietly(ndjsonFile);
            MDC.clear();
        }
    }

    private void doRun(UUID versionId, Path ndjsonFile) {
        IndexVersion version = stateStore.markIndexVersionAsBuilding(versionId);
        SearchIndex index = stateStore.getIndex(version.getIndex().getId());
        UUID indexId = version.getIndex().getId();
        Path versionDir = properties.versionDir(indexId, versionId);
        log.debug("Starting build pipeline for index '{}' version {} in {}", index.getName(), version.getVersion(), versionDir);
        try {
            Path luceneDir = versionDir.resolve("index");
            Path tarFile = versionDir.resolve("index.tar.gz");

            long docCount = indexBuilder.build(index, ndjsonFile, luceneDir);
            log.debug("Lucene build complete for version {}: {} docs", versionId, docCount);

            ArtifactPackager.ArtifactResult artifact = packager.packageTo(luceneDir, tarFile);
            log.debug("Artifact packaged for version {}: {} bytes, sha256={}", versionId, artifact.size(), artifact.checksum());

            stateStore.markIndexVersionAsBuilt(versionId, docCount, artifact.size(), artifact.checksum());

            String artifactKey = indexId + "/" + version.getVersion() + "/index.tar.gz";
            storage.upload(artifactKey, artifact.path());
            try {
                stateStore.markIndexVersionAsUploaded(versionId, artifactKey);
            } catch (RuntimeException e) {
                deleteOrphanedArtifact(artifactKey);
                throw e;
            }

            log.info("Build of version {} uploaded to {} ({} docs, {} bytes, sha256={})",
                    versionId, artifactKey, docCount, artifact.size(), artifact.checksum());
        } finally {
            deleteQuietly(versionDir);
        }
    }

    private void deleteOrphanedArtifact(String artifactKey) {
        try {
            storage.delete(artifactKey);
        } catch (Exception e) {
            log.warn("Failed to delete orphaned artifact {} after a failed status update", artifactKey, e);
        }
    }

    private void failSafely(UUID versionId, Throwable cause) {
        String message = cause.getMessage();
        if (message == null) {
            message = cause.getClass().getSimpleName();
        }
        log.warn("Build of version {} failed: {}", versionId, message, cause);
        try {
            stateStore.markIndexVersionAsFailed(versionId, message);
        } catch (Exception e) {
            log.error("Failed to mark version {} as FAILED", versionId, e);
        }
    }

    private void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            FileSystemUtils.deleteRecursively(path);
        } catch (IOException e) {
            log.warn("Failed to delete {}", path, e);
        }
    }
}
