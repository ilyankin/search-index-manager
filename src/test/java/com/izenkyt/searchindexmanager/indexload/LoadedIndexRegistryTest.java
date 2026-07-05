package com.izenkyt.searchindexmanager.indexload;

import com.izenkyt.searchindexmanager.common.NotFoundException;
import com.izenkyt.searchindexmanager.indexload.LoadedIndexRegistry.BeginLoadResult;
import com.izenkyt.searchindexmanager.indexload.LoadedIndexRegistry.LoadKey;
import com.izenkyt.searchindexmanager.indexload.LoadedIndexRegistry.LoadState;
import org.apache.lucene.index.DirectoryReader;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class LoadedIndexRegistryTest {

    private final LoadedIndexRegistry registry = new LoadedIndexRegistry();
    private final LoadKey key = new LoadKey(UUID.randomUUID(), 1);

    @Test
    void tryBeginLoad_newKey_returnsNewLoadingEntry() {
        BeginLoadResult result = registry.tryBeginLoad(key);

        assertThat(result.isNew()).isTrue();
        assertThat(result.entry().state()).isEqualTo(LoadState.LOADING);
    }

    @Test
    void tryBeginLoad_whileLoading_isNoOp() {
        BeginLoadResult first = registry.tryBeginLoad(key);
        BeginLoadResult second = registry.tryBeginLoad(key);

        assertThat(second.isNew()).isFalse();
        assertThat(second.entry()).isSameAs(first.entry());
        assertThat(second.entry().state()).isEqualTo(LoadState.LOADING);
    }

    @Test
    void tryBeginLoad_afterLoaded_isNoOp() {
        registry.tryBeginLoad(key);
        registry.markLoaded(key, mock(DirectoryReader.class), 5L, 100L);

        BeginLoadResult result = registry.tryBeginLoad(key);

        assertThat(result.isNew()).isFalse();
        assertThat(result.entry().state()).isEqualTo(LoadState.LOADED);
    }

    @Test
    void tryBeginLoad_afterFailed_startsNewAttempt() {
        registry.tryBeginLoad(key);
        registry.markFailed(key, "boom");

        BeginLoadResult result = registry.tryBeginLoad(key);

        assertThat(result.isNew()).isTrue();
        assertThat(result.entry().state()).isEqualTo(LoadState.LOADING);
    }

    @Test
    void unload_whileLoading_throwsLoadInProgressException() {
        registry.tryBeginLoad(key);

        assertThatThrownBy(() -> registry.unload(key))
                .isInstanceOf(LoadInProgressException.class);
    }

    @Test
    void unload_missingKey_throwsNotFoundException() {
        assertThatThrownBy(() -> registry.unload(key))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void unload_removesEntryAndClosesReader() throws Exception {
        DirectoryReader reader = mock(DirectoryReader.class);
        registry.tryBeginLoad(key);
        registry.markLoaded(key, reader, 5L, 100L);

        registry.unload(key);

        verify(reader).close();
        assertThat(registry.list()).isEmpty();
    }

    @Test
    void list_returnsAllEntriesIncludingLoadingAndFailed() {
        LoadKey loading = new LoadKey(UUID.randomUUID(), 1);
        LoadKey failed = new LoadKey(UUID.randomUUID(), 2);
        registry.tryBeginLoad(loading);
        registry.tryBeginLoad(failed);
        registry.markFailed(failed, "boom");

        assertThat(registry.list()).hasSize(2);
    }
}
