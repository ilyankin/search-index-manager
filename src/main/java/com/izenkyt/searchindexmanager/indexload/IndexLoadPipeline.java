package com.izenkyt.searchindexmanager.indexload;

import com.izenkyt.searchindexmanager.indexload.LoadedIndexRegistry.LoadKey;
import com.izenkyt.searchindexmanager.storage.ArtifactStorage;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexFormatTooOldException;
import org.apache.lucene.store.FSDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class IndexLoadPipeline {

    private static final Logger log = LoggerFactory.getLogger(IndexLoadPipeline.class);

    private final ArtifactStorage storage;
    private final TarArchiveExtractor extractor;
    private final LoadedIndexRegistry registry;
    private final IndexLoadProperties properties;

    public IndexLoadPipeline(ArtifactStorage storage,
                              TarArchiveExtractor extractor,
                              LoadedIndexRegistry registry,
                              IndexLoadProperties properties) {
        this.storage = storage;
        this.extractor = extractor;
        this.registry = registry;
        this.properties = properties;
    }

    @Async("loadExecutor")
    public void run(LoadKey key, String artifactKey, long expectedSize, String expectedChecksum, long expectedDocCount) {
        MDC.put("indexId", key.indexId().toString());
        MDC.put("version", String.valueOf(key.version()));
        try {
            doRun(key, artifactKey, expectedSize, expectedChecksum, expectedDocCount);
        } catch (Exception e) {
            failSafely(key, e);
        } finally {
            MDC.clear();
        }
    }

    private void doRun(LoadKey key, String artifactKey, long expectedSize, String expectedChecksum, long expectedDocCount)
            throws IOException {
        Path work = createWorkDir();
        Path finalDir = properties.targetDir(key.indexId(), key.version());
        try {
            Path downloaded = work.resolve("index.tar.gz");
            storage.downloadTo(artifactKey, downloaded);
            verifyIntegrity(downloaded, expectedSize, expectedChecksum);

            Path extractedRoot = work.resolve("extracted");
            long sizeOnDisk = extractor.extract(downloaded, extractedRoot);
            Path luceneDir = extractedRoot.resolve("index");

            Files.createDirectories(finalDir.getParent());
            Files.move(luceneDir, finalDir, StandardCopyOption.ATOMIC_MOVE);

            DirectoryReader reader = openReader(finalDir);
            long numDocs;
            try {
                numDocs = reader.numDocs();
                verifyDocCount(numDocs, expectedDocCount);
            } catch (RuntimeException e) {
                closeQuietly(reader);
                throw e;
            }

            registry.markLoaded(key, reader, numDocs, sizeOnDisk);
        } catch (Exception e) {
            WorkdirCleanup.deleteQuietly(finalDir);
            throw e;
        } finally {
            WorkdirCleanup.deleteQuietly(work);
        }
    }

    private Path createWorkDir() throws IOException {
        Path base = Path.of(properties.getDir());
        Files.createDirectories(base);
        return Files.createTempDirectory(base, "tmp-");
    }

    private void verifyIntegrity(Path file, long expectedSize, String expectedChecksum) throws IOException {
        long actualSize = Files.size(file);
        String actualChecksum = sha256Hex(file);
        if (actualSize != expectedSize || !actualChecksum.equals(expectedChecksum)) {
            throw new IndexLoadException("Downloaded artifact integrity check failed: expected size="
                    + expectedSize + " checksum=" + expectedChecksum + ", got size=" + actualSize
                    + " checksum=" + actualChecksum);
        }
    }

    private String sha256Hex(Path file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IndexLoadException("SHA-256 algorithm not available", e);
        }
        try (InputStream in = Files.newInputStream(file);
             DigestInputStream digestIn = new DigestInputStream(in, digest)) {
            digestIn.transferTo(java.io.OutputStream.nullOutputStream());
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private DirectoryReader openReader(Path dir) throws IOException {
        try {
            return DirectoryReader.open(FSDirectory.open(dir));
        } catch (IndexFormatTooOldException e) {
            throw new IndexLoadException(
                    "Index was built with an incompatible (older) Lucene index format and cannot be opened: "
                            + e.getMessage(), e);
        }
    }

    private void verifyDocCount(long actualDocCount, long expectedDocCount) {
        if (actualDocCount != expectedDocCount) {
            throw new IndexLoadException("Loaded index doc count mismatch: expected " + expectedDocCount
                    + " but found " + actualDocCount);
        }
    }

    private void failSafely(LoadKey key, Throwable cause) {
        String message = cause.getMessage();
        if (message == null) {
            message = cause.getClass().getSimpleName();
        }
        log.warn("Load {} failed: {}", key, message, cause);
        try {
            registry.markFailed(key, message);
        } catch (Exception e) {
            log.error("Failed to mark load {} as FAILED", key, e);
        }
    }

    private void closeQuietly(DirectoryReader reader) {
        try {
            reader.close();
        } catch (IOException e) {
            log.warn("Failed to close reader after failed verification", e);
        }
    }
}
