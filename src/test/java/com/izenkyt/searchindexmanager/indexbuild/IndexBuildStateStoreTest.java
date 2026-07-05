package com.izenkyt.searchindexmanager.indexbuild;

import com.izenkyt.searchindexmanager.index.IndexVersion;
import com.izenkyt.searchindexmanager.index.IndexVersionRepository;
import com.izenkyt.searchindexmanager.index.IndexVersionStatus;
import com.izenkyt.searchindexmanager.index.SearchIndex;
import com.izenkyt.searchindexmanager.index.SearchIndexRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class IndexBuildStateStoreTest {

    @Mock
    private IndexVersionRepository versionRepository;

    @Mock
    private SearchIndexRepository searchIndexRepository;

    private IndexBuildStateStore stateStore;

    @BeforeEach
    void setUp() {
        stateStore = new IndexBuildStateStore(versionRepository, searchIndexRepository);
    }

    @Test
    void markIndexVersionAsFailed_ignoresStaleFailure_whenAlreadyReady() {
        UUID versionId = UUID.randomUUID();
        SearchIndex index = new SearchIndex("idx", "d", Map.of("t", "text"));
        IndexVersion version = new IndexVersion(index, 1, IndexVersionStatus.READY);
        version.setArtifactKey("idx/1/index.tar.gz");
        version.setDocCount(2L);
        given(versionRepository.findById(versionId)).willReturn(Optional.of(version));

        IndexVersion result = stateStore.markIndexVersionAsFailed(versionId, "stale ambiguous-publish failure");

        assertThat(result.getStatus()).isEqualTo(IndexVersionStatus.READY);
        assertThat(result.getArtifactKey()).isEqualTo("idx/1/index.tar.gz");
        assertThat(result.getDocCount()).isEqualTo(2L);
        assertThat(result.getErrorMessage()).isNull();
    }

    @Test
    void markIndexVersionAsFailed_marksFailed_whenUploaded() {
        UUID versionId = UUID.randomUUID();
        SearchIndex index = new SearchIndex("idx", "d", Map.of("t", "text"));
        IndexVersion version = new IndexVersion(index, 1, IndexVersionStatus.UPLOADED);
        version.setArtifactKey("idx/1/index.tar.gz");
        given(versionRepository.findById(versionId)).willReturn(Optional.of(version));

        IndexVersion result = stateStore.markIndexVersionAsFailed(versionId, "kafka unreachable");

        assertThat(result.getStatus()).isEqualTo(IndexVersionStatus.FAILED);
        assertThat(result.getArtifactKey()).isNull();
        assertThat(result.getErrorMessage()).isEqualTo("kafka unreachable");
    }
}
