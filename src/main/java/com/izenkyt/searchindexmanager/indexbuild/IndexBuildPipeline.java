package com.izenkyt.searchindexmanager.indexbuild;

import com.izenkyt.searchindexmanager.index.IndexVersion;
import com.izenkyt.searchindexmanager.index.SearchIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;

@Component
public class IndexBuildPipeline {

    private static final Logger log = LoggerFactory.getLogger(IndexBuildPipeline.class);

    private final IndexBuildStateStore stateStore;
    private final LuceneIndexBuilder indexBuilder;
    private final ArtifactPackager packager;
    private final IndexBuildProperties properties;

    public IndexBuildPipeline(IndexBuildStateStore stateStore,
                              LuceneIndexBuilder indexBuilder,
                              ArtifactPackager packager,
                              IndexBuildProperties properties) {
        this.stateStore = stateStore;
        this.indexBuilder = indexBuilder;
        this.packager = packager;
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
            cleanupTemp(ndjsonFile);
            MDC.clear();
        }
    }

    private void doRun(UUID versionId, Path ndjsonFile) {
        IndexVersion version = stateStore.markIndexVersionAsBuilding(versionId);
        SearchIndex index = stateStore.getIndex(version.getIndex().getId());

        Path versionDir = properties.versionDir(version.getIndex().getId(), versionId);
        log.debug("Starting build pipeline for index '{}' version {} in {}", index.getName(), version.getVersion(), versionDir);
        Path luceneDir = versionDir.resolve("index");
        Path tarFile = versionDir.resolve("index.tar.gz");

        long docCount = indexBuilder.build(index, ndjsonFile, luceneDir);
        log.debug("Lucene build complete for version {}: {} docs", versionId, docCount);
        ArtifactPackager.ArtifactResult artifact = packager.packageTo(luceneDir, tarFile);
        log.debug("Artifact packaged for version {}: {} bytes, sha256={}", versionId, artifact.size(), artifact.checksum());

        stateStore.markIndexVersionAsBuilt(versionId, docCount, artifact.size(), artifact.checksum(), tarFile.toString());
        cleanupLuceneDir(luceneDir);
        log.info("Build of version {} completed: {} docs, {} bytes, sha256={}",
                versionId, docCount, artifact.size(), artifact.checksum());
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

    private void cleanupTemp(Path ndjsonFile) {
        deleteQuietly(ndjsonFile);
    }

    private void cleanupLuceneDir(Path luceneDir) {
        deleteQuietly(luceneDir);
    }

    private void deleteQuietly(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (var paths = Files.walk(path)) {
            paths.sorted(Comparator.reverseOrder()) // From nested ones
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException e) {
                            log.warn("Failed to delete {}", p, e);
                        }
                    });
        } catch (IOException e) {
            log.warn("Failed to walk {}", path, e);
        }
    }
}
