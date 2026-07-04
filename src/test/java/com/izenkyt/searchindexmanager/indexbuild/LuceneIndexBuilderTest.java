package com.izenkyt.searchindexmanager.indexbuild;

import com.izenkyt.searchindexmanager.index.SearchIndex;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.IOUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LuceneIndexBuilderTest {

    private final ObjectMapper ndjsonMapper = new ObjectMapper();
    private final LuceneIndexBuilder builder = new LuceneIndexBuilder(ndjsonMapper);

    @TempDir
    private Path tempDir;

    private SearchIndex newIndex() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("title", "text");
        fields.put("tag", "keyword");
        fields.put("count", "long");
        return new SearchIndex("test-idx", "desc", fields);
    }

    // Each line must stay a single physical line in the file: LuceneIndexBuilder reads and
    // reports errors by physical line number, so pretty-printed JSON is compacted back to one line here.
    private Path writeNdjson(String... lines) throws IOException {
        Path file = tempDir.resolve("documents.ndjson");
        String[] normalized = new String[lines.length];
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].strip();
            normalized[i] = line.isEmpty() ? "" : ndjsonMapper.writeValueAsString(ndjsonMapper.readTree(line));
        }
        Files.writeString(file, String.join("\n", normalized) + "\n");
        return file;
    }

    @Test
    void buildsIndexThatReallySearchesByKeywordAndText() throws IOException {
        SearchIndex index = newIndex();
        Path ndjson = writeNdjson(
                        """
                        {
                          "title": "Hello World",
                          "tag": "java",
                          "count": 42
                        }
                        """,
                        """
                        {
                          "title": "Second document",
                          "tag": "python",
                          "count": 7
                        }
                        """
        );
        Path luceneDir = tempDir.resolve("index");

        long docCount = builder.build(index, ndjson, luceneDir);

        assertThat(docCount).isEqualTo(2);

        try (var directory = FSDirectory.open(luceneDir);
             var reader = DirectoryReader.open(directory)) {
            assertThat(reader.numDocs()).isEqualTo(2);

            IndexSearcher searcher = new IndexSearcher(reader);

            Query keywordQuery = new TermQuery(new Term("tag", "java"));
            TopDocs keywordHits = searcher.search(keywordQuery, 10);
            assertThat(keywordHits.scoreDocs).hasSize(1);
            Document keywordDoc = searcher.storedFields().document(keywordHits.scoreDocs[0].doc);
            assertThat(keywordDoc.get("title")).isEqualTo("Hello World");

            Query textQuery = new TermQuery(new Term("title", "hello"));
            TopDocs textHits = searcher.search(textQuery, 10);
            assertThat(textHits.scoreDocs).hasSize(1);
            Document textDoc = searcher.storedFields().document(textHits.scoreDocs[0].doc);
            assertThat(textDoc.get("tag")).isEqualTo("java");
        } finally {
            IOUtils.rm(luceneDir);
        }
    }

    @Test
    void buildsIndexAndSearchesByExactKeywordNotPartialMatch() throws IOException {
        SearchIndex index = newIndex();
        Path ndjson = writeNdjson(
                        """
                        {
                          "title": "one",
                          "tag": "java",
                          "count": 1
                        }
                        """,
                        """
                        {
                          "title": "two",
                          "tag": "javascript",
                          "count": 2
                        }
                        """
        );
        Path luceneDir = tempDir.resolve("index");

        builder.build(index, ndjson, luceneDir);

        try (var directory = FSDirectory.open(luceneDir);
             var reader = DirectoryReader.open(directory)) {
            IndexSearcher searcher = new IndexSearcher(reader);

            TopDocs javaHits = searcher.search(new TermQuery(new Term("tag", "java")), 10);
            assertThat(javaHits.scoreDocs).hasSize(1);
            TopDocs jsHits = searcher.search(new TermQuery(new Term("tag", "javascript")), 10);
            assertThat(jsHits.scoreDocs).hasSize(1);
        } finally {
            IOUtils.rm(luceneDir);
        }
    }

    @Test
    void blankLinesAreSkippedButLineNumbersPreserved() throws IOException {
        SearchIndex index = newIndex();
        Path ndjson = writeNdjson(
                        """
                        {
                          "title": "first",
                          "tag": "a",
                          "count": 1
                        }
                        """,
                        "",
                        "   ",
                        """
                        {
                          "title": "second",
                          "tag": "b",
                          "count": 2
                        }
                        """
        );
        Path luceneDir = tempDir.resolve("index");

        long docCount = builder.build(index, ndjson, luceneDir);

        assertThat(docCount).isEqualTo(2);
        IOUtils.rm(luceneDir);
    }

    @Test
    void buildFailsOnUnknownFieldWithLineNumber() throws IOException {
        SearchIndex index = newIndex();
        Path ndjson = writeNdjson(
                        """
                        {
                          "title": "first",
                          "tag": "a",
                          "count": 1
                        }
                        """,
                        """
                        {
                          "title": "second",
                          "tag": "b",
                          "count": 2,
                          "bogus": "x"
                        }
                        """
        );
        Path luceneDir = tempDir.resolve("index");

        assertThatThrownBy(() -> builder.build(index, ndjson, luceneDir))
                .isInstanceOf(IndexBuildException.class)
                .hasMessageContaining("Line 2")
                .hasMessageContaining("bogus");

        IOUtils.rm(luceneDir);
    }

    @Test
    void buildFailsOnTypeMismatch() throws IOException {
        SearchIndex index = newIndex();
        Path ndjson = writeNdjson(
                        """
                        {
                          "title": "ok",
                          "tag": "a",
                          "count": "not a number"
                        }
                        """);
        Path luceneDir = tempDir.resolve("index");

        assertThatThrownBy(() -> builder.build(index, ndjson, luceneDir))
                .isInstanceOf(IndexBuildException.class)
                .hasMessageContaining("Line 1")
                .hasMessageContaining("count")
                .hasMessageContaining("long");

        IOUtils.rm(luceneDir);
    }
}
