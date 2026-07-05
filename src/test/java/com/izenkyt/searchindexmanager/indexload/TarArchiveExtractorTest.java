package com.izenkyt.searchindexmanager.indexload;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.archivers.tar.TarConstants;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.store.FSDirectory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TarArchiveExtractorTest {

    private final TarArchiveExtractor extractor = new TarArchiveExtractor();

    @TempDir
    private Path tempDir;

    @Test
    void extract_happyPath_producesOpenableLuceneIndex() throws IOException {
        Path tarFile = IndexLoadTestSupport.packageSampleIndex(tempDir, "extractor-idx").path();
        Path targetDir = tempDir.resolve("target");

        extractor.extract(tarFile, targetDir);

        try (var directory = FSDirectory.open(targetDir.resolve("index"));
             var reader = DirectoryReader.open(directory)) {
            assertThat(reader.numDocs()).isEqualTo(2);
        }
    }

    @Test
    void extract_withPathTraversalEntry_throwsAndDoesNotEscapeTarget() throws IOException {
        Path tarFile = tempDir.resolve("evil-traversal.tar.gz");
        writeTarWithPathTraversalEntry(tarFile);
        Path targetDir = tempDir.resolve("target-traversal");

        assertThatThrownBy(() -> extractor.extract(tarFile, targetDir))
                .isInstanceOf(IndexLoadException.class);

        assertThat(tempDir.resolve("evil.txt")).doesNotExist();
    }

    @Test
    void extract_withUnNormalizedTargetDir_stillExtracts() throws IOException {
        // Regression for docs/bugs/stage-7-load-zip-slip-false-positive.md: a targetDir containing
        // a redundant "." component (as the real "./load-work" default config produces) must not be
        // rejected as zip-slip once resolveSafely normalizes both sides consistently.
        Path tarFile = IndexLoadTestSupport.packageSampleIndex(tempDir, "extractor-idx-relative").path();
        Path targetDir = tempDir.resolve(Path.of(".", "target-relative"));

        extractor.extract(tarFile, targetDir);

        try (var directory = FSDirectory.open(targetDir.resolve("index"));
             var reader = DirectoryReader.open(directory)) {
            assertThat(reader.numDocs()).isEqualTo(2);
        }
    }

    @Test
    void extract_withSymlinkEntry_throwsAndDoesNotCreateLink() throws IOException {
        Path tarFile = tempDir.resolve("evil-symlink.tar.gz");
        writeTarWithSymlinkEntry(tarFile);
        Path targetDir = tempDir.resolve("target-symlink");

        assertThatThrownBy(() -> extractor.extract(tarFile, targetDir))
                .isInstanceOf(IndexLoadException.class);

        assertThat(targetDir.resolve("link")).doesNotExist();
    }

    private void writeTarWithPathTraversalEntry(Path tarFile) throws IOException {
        try (OutputStream fos = Files.newOutputStream(tarFile);
             GzipCompressorOutputStream gz = new GzipCompressorOutputStream(fos);
             TarArchiveOutputStream tar = new TarArchiveOutputStream(gz)) {
            byte[] payload = "pwned".getBytes();
            TarArchiveEntry entry = new TarArchiveEntry("../evil.txt");
            entry.setSize(payload.length);
            tar.putArchiveEntry(entry);
            tar.write(payload);
            tar.closeArchiveEntry();
            tar.finish();
        }
    }

    private void writeTarWithSymlinkEntry(Path tarFile) throws IOException {
        try (OutputStream fos = Files.newOutputStream(tarFile);
             GzipCompressorOutputStream gz = new GzipCompressorOutputStream(fos);
             TarArchiveOutputStream tar = new TarArchiveOutputStream(gz)) {
            TarArchiveEntry entry = new TarArchiveEntry("link", TarConstants.LF_SYMLINK);
            entry.setLinkName("/etc/passwd");
            tar.putArchiveEntry(entry);
            tar.closeArchiveEntry();
            tar.finish();
        }
    }
}
