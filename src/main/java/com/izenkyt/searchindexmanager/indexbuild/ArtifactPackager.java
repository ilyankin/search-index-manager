package com.izenkyt.searchindexmanager.indexbuild;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Iterator;

// Так как Lucene пишет индекс как директорию с кучей файлов, разделённые на сегменты.
// То для хранения в MinIO нужно преобразовать в один BLOB (tar.gz)
@Component
public class ArtifactPackager {

    private static final Logger log = LoggerFactory.getLogger(ArtifactPackager.class);

    public ArtifactResult packageTo(Path luceneDir, Path tarFile) {
        log.debug("Packaging {} into {}", luceneDir, tarFile);
        try {
            Files.createDirectories(tarFile.getParent());
        } catch (IOException e) {
            throw new IndexBuildException("Failed to create artifact directory " + tarFile.getParent(), e);
        }
        MessageDigest digest = sha256();
        try (OutputStream fos = Files.newOutputStream(tarFile);
             DigestOutputStream digestOut = new DigestOutputStream(fos, digest);
             BufferedOutputStream bos = new BufferedOutputStream(digestOut);
             GzipCompressorOutputStream gz = new GzipCompressorOutputStream(bos);
             TarArchiveOutputStream tar = new TarArchiveOutputStream(gz)) {

            writeDirectory(tar, luceneDir);
            tar.finish();
            tar.flush();
        } catch (IOException e) {
            throw new IndexBuildException("Failed to package artifact: " + e.getMessage(), e);
        }
        long size;
        try {
            size = Files.size(tarFile);
        } catch (IOException e) {
            throw new IndexBuildException("Failed to read artifact size: " + e.getMessage(), e);
        }
        String checksum = HexFormat.of().formatHex(digest.digest());
        log.debug("Artifact packaged: {} bytes, sha256={}", size, checksum);
        return new ArtifactResult(tarFile, size, checksum);
    }

    private void writeDirectory(TarArchiveOutputStream tar, Path root) throws IOException {
        try (var paths = Files.walk(root)) {
            Iterator<Path> files = paths.filter(Files::isRegularFile).iterator();
            while (files.hasNext()) {
                Path file = files.next();
                String entryName = "index/" + root.relativize(file);
                TarArchiveEntry entry = new TarArchiveEntry(entryName);
                entry.setSize(Files.size(file));
                tar.putArchiveEntry(entry);
                try (InputStream in = Files.newInputStream(file)) {
                    in.transferTo(tar);
                }
                tar.closeArchiveEntry();
            }
        }
    }

    private MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IndexBuildException("SHA-256 algorithm not available", e);
        }
    }

    public record ArtifactResult(Path path, long size, String checksum) {
    }
}
