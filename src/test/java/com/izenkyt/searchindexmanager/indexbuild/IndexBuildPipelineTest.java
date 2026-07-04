package com.izenkyt.searchindexmanager.indexbuild;

import com.izenkyt.searchindexmanager.index.IndexVersion;
import com.izenkyt.searchindexmanager.index.IndexVersionStatus;
import com.izenkyt.searchindexmanager.index.SearchIndex;
import com.izenkyt.searchindexmanager.storage.ArtifactStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class IndexBuildPipelineTest {

    @Mock
    private IndexBuildStateStore stateStore;

    @Mock
    private LuceneIndexBuilder indexBuilder;

    @Mock
    private ArtifactPackager packager;

    @Mock
    private ArtifactStorage storage;

    @TempDir
    Path workdir;

    private IndexBuildPipeline pipeline;
    private UUID indexId;
    private UUID versionId;
    private SearchIndex index;

    @BeforeEach
    void setUp() {
        IndexBuildProperties properties = new IndexBuildProperties();
        properties.setWorkdir(workdir.toString());
        pipeline = new IndexBuildPipeline(stateStore, indexBuilder, packager, storage, properties);

        indexId = UUID.randomUUID();
        versionId = UUID.randomUUID();
        index = new SearchIndex("idx", "d", Map.of("t", "text"));
        ReflectionTestUtils.setField(index, "id", indexId);
        IndexVersion version = new IndexVersion(index, 1, IndexVersionStatus.BUILDING);

        given(stateStore.markIndexVersionAsBuilding(versionId)).willReturn(version);
        given(stateStore.getIndex(indexId)).willReturn(index);
        given(indexBuilder.build(eq(index), any(Path.class), any(Path.class))).willReturn(2L);
        given(packager.packageTo(any(Path.class), any(Path.class)))
                .willReturn(new ArtifactPackager.ArtifactResult(workdir.resolve("index.tar.gz"), 123L, "abc123"));
    }

    @Test
    void run_deletesOrphanedArtifact_whenMarkUploadedFailsAfterSuccessfulUpload() {
        String expectedKey = indexId + "/1/index.tar.gz";
        given(stateStore.markIndexVersionAsUploaded(versionId, expectedKey))
                .willThrow(new RuntimeException("db down"));

        pipeline.run(versionId, workdir.resolve("input.ndjson"));

        verify(storage).upload(expectedKey, workdir.resolve("index.tar.gz"));
        verify(storage).delete(expectedKey);
        verify(stateStore).markIndexVersionAsFailed(eq(versionId), anyString());
    }
}
