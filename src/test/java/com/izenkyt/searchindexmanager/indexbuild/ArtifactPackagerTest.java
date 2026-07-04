package com.izenkyt.searchindexmanager.indexbuild;

import com.izenkyt.searchindexmanager.index.SearchIndex;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.IOUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ArtifactPackagerTest {
    private final ArtifactPackager packager = new ArtifactPackager();
    private final LuceneIndexBuilder builder = new LuceneIndexBuilder(new ObjectMapper());

    @TempDir
    private Path tempDir;

    @Test
    void packagedArtifactUnpacksToWorkingIndex() throws IOException {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("title", "text");
        fields.put("tag", "keyword");
        SearchIndex index = new SearchIndex("pkg-idx", "desc", fields);

        Path workdir = tempDir.resolve("work");
        Path ndjson = workdir.resolve("documents.ndjson");
        Files.createDirectories(workdir);
        Files.writeString(ndjson,
                "{\"title\":\"Hello World\",\"tag\":\"java\"}\n"
                        + "{\"title\":\"Second doc\",\"tag\":\"python\"}\n");
        Path luceneDir = workdir.resolve("index");
        builder.build(index, ndjson, luceneDir);

        Path tarFile = tempDir.resolve("index.tar.gz");
        ArtifactPackager.ArtifactResult result = packager.packageTo(luceneDir, tarFile);

        assertThat(result.path()).isEqualTo(tarFile);
        assertThat(result.size()).isPositive();
        assertThat(result.checksum()).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(result.checksum()).isEqualTo(sha256Hex(tarFile));

        Path unpacked = tempDir.resolve("unpacked");
        extractTarGz(tarFile, unpacked);

        Path unpackedIndex = unpacked.resolve("index");
        try (var directory = FSDirectory.open(unpackedIndex);
             var reader = DirectoryReader.open(directory)) {
            assertThat(reader.numDocs()).isEqualTo(2);
            IndexSearcher searcher = new IndexSearcher(reader);
            TopDocs hits = searcher.search(new TermQuery(new Term("tag", "java")), 10);
            assertThat(hits.scoreDocs).hasSize(1);
            Document doc = searcher.storedFields().document(hits.scoreDocs[0].doc);
            assertThat(doc.get("title")).isEqualTo("Hello World");
        }

        IOUtils.rm(luceneDir);
    }

    private String sha256Hex(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(Files.readAllBytes(file));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private void extractTarGz(Path tarGz, Path target) throws IOException {
        Files.createDirectories(target);
        try (InputStream fis = Files.newInputStream(tarGz);
             BufferedInputStream bis = new BufferedInputStream(fis);
             GzipCompressorInputStream gz = new GzipCompressorInputStream(bis);
             TarArchiveInputStream tar = new TarArchiveInputStream(gz)) {
            TarArchiveEntry entry;
            while ((entry = tar.getNextEntry()) != null) {
                Path out = target.resolve(entry.getName()).normalize();
                if (!out.startsWith(target)) {
                    throw new IOException("Bad tar entry path: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(out);
                } else {
                    Files.createDirectories(out.getParent());
                    Files.copy(tar, out, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }
}
