package com.izenkyt.searchindexmanager.indexbuild;

import com.izenkyt.searchindexmanager.index.SearchIndex;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@Component
public class LuceneIndexBuilder {

    private static final Logger log = LoggerFactory.getLogger(LuceneIndexBuilder.class);
    private static final TypeReference<Map<String, Object>> ROW_TYPE = new TypeReference<>() {};

    private final ObjectMapper objectMapper;

    public LuceneIndexBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public long build(SearchIndex index, Path ndjsonFile, Path luceneDir) {
        log.debug("Building Lucene index for '{}' from {} into {}", index.getName(), ndjsonFile, luceneDir);
        DocumentMapper mapper = new DocumentMapper(index.getFields());

        try (Analyzer analyzer = new StandardAnalyzer(); Directory directory = FSDirectory.open(luceneDir)) {
            long docCount = writeDocuments(directory, analyzer, mapper, ndjsonFile);
            verify(directory, docCount);
            log.debug("Lucene index for '{}' built and verified: {} docs", index.getName(), docCount);
            return docCount;
        } catch (IOException e) {
            throw new IndexBuildException("Failed to build Lucene index: " + e.getMessage(), e);
        }
    }

    private long writeDocuments(Directory directory, Analyzer analyzer, DocumentMapper mapper, Path ndjsonFile)
            throws IOException {
        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        config.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
        long docCount = 0;
        try (IndexWriter writer = new IndexWriter(directory, config);
             BufferedReader reader = Files.newBufferedReader(ndjsonFile)) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) {
                    continue;
                }
                Map<String, Object> row = objectMapper.readValue(line, ROW_TYPE);
                writer.addDocument(mapper.toLuceneDocument(row, lineNumber));
                docCount++;
            }
            writer.commit();
        }
        return docCount;
    }

    private void verify(Directory directory, long docCount) throws IOException {
        try (DirectoryReader reader = DirectoryReader.open(directory)) {
            if (reader.numDocs() != docCount) {
                throw new IndexBuildException("Index verification failed: expected " + docCount
                        + " docs but found " + reader.numDocs());
            }
        }
    }
}
