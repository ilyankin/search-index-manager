package com.izenkyt.searchindexmanager.indexload;

import com.izenkyt.searchindexmanager.index.SearchIndex;
import com.izenkyt.searchindexmanager.indexbuild.ArtifactPackager;
import com.izenkyt.searchindexmanager.indexbuild.LuceneIndexBuilder;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

final class IndexLoadTestSupport {

    private IndexLoadTestSupport() {
    }

    static ArtifactPackager.ArtifactResult packageSampleIndex(Path tempDir, String indexName) throws IOException {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("title", "text");
        fields.put("tag", "keyword");
        SearchIndex index = new SearchIndex(indexName, "desc", fields);

        Path workdir = tempDir.resolve("build-work");
        Path ndjson = workdir.resolve("documents.ndjson");
        Files.createDirectories(workdir);
        Files.writeString(ndjson,
                "{\"title\":\"Hello World\",\"tag\":\"java\"}\n"
                        + "{\"title\":\"Second doc\",\"tag\":\"python\"}\n");
        Path luceneDir = workdir.resolve("index");
        new LuceneIndexBuilder(new ObjectMapper()).build(index, ndjson, luceneDir);

        Path tarFile = tempDir.resolve("index.tar.gz");
        return new ArtifactPackager().packageTo(luceneDir, tarFile);
    }
}
