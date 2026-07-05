package com.izenkyt.searchindexmanager.indexload;

import com.izenkyt.searchindexmanager.common.NotFoundException;
import com.izenkyt.searchindexmanager.index.ArtifactNotAvailableException;
import com.izenkyt.searchindexmanager.index.IndexCatalogService;
import com.izenkyt.searchindexmanager.index.api.IndexController;
import com.izenkyt.searchindexmanager.indexload.LoadedIndexRegistry.BeginLoadResult;
import com.izenkyt.searchindexmanager.indexload.LoadedIndexRegistry.LoadKey;
import org.apache.lucene.index.DirectoryReader;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

@WebMvcTest(controllers = {IndexLoadController.class, IndexController.class})
class IndexLoadControllerWebMvcTest {

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private IndexLoadService loadService;

    @MockitoBean
    private IndexCatalogService catalogService;

    private final LoadedIndexRegistry registry = new LoadedIndexRegistry();

    @Test
    void load_returns202_whenNewAttempt() {
        UUID indexId = UUID.randomUUID();
        LoadKey key = new LoadKey(indexId, 1);
        BeginLoadResult result = new BeginLoadResult(registry.tryBeginLoad(key).entry(), true);
        given(loadService.startLoad(indexId, 1)).willReturn(result);

        assertThat(mvc.post().uri("/api/v1/indexes/" + indexId + "/versions/1/load"))
                .hasStatus(HttpStatus.ACCEPTED)
                .bodyJson().extractingPath("$.state").isEqualTo("LOADING");
    }

    @Test
    void load_returns200_whenAlreadyLoadingOrLoaded() {
        UUID indexId = UUID.randomUUID();
        LoadKey key = new LoadKey(indexId, 1);
        registry.tryBeginLoad(key);
        registry.markLoaded(key, mock(DirectoryReader.class), 5L, 100L);
        BeginLoadResult result = new BeginLoadResult(registry.list().get(0), false);
        given(loadService.startLoad(indexId, 1)).willReturn(result);

        assertThat(mvc.post().uri("/api/v1/indexes/" + indexId + "/versions/1/load"))
                .hasStatus(HttpStatus.OK)
                .bodyJson().extractingPath("$.state").isEqualTo("LOADED");
    }

    @Test
    void load_returns404_whenVersionMissing() {
        UUID indexId = UUID.randomUUID();
        given(loadService.startLoad(indexId, 1))
                .willThrow(new NotFoundException("Version 1 not found for index " + indexId));

        assertThat(mvc.post().uri("/api/v1/indexes/" + indexId + "/versions/1/load"))
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void load_returns409_whenArtifactNotAvailable() {
        UUID indexId = UUID.randomUUID();
        given(loadService.startLoad(indexId, 1))
                .willThrow(new ArtifactNotAvailableException(
                        "Artifact for version 1 of index " + indexId + " is not available"));

        assertThat(mvc.post().uri("/api/v1/indexes/" + indexId + "/versions/1/load"))
                .hasStatus(HttpStatus.CONFLICT);
    }

    @Test
    void unload_returns204_onSuccess() {
        UUID indexId = UUID.randomUUID();

        assertThat(mvc.delete().uri("/api/v1/indexes/" + indexId + "/versions/1/load"))
                .hasStatus(HttpStatus.NO_CONTENT);
    }

    @Test
    void unload_returns404_whenNotInRegistry() {
        UUID indexId = UUID.randomUUID();
        doThrow(new NotFoundException("Loaded index not found"))
                .when(loadService).unload(indexId, 1);

        assertThat(mvc.delete().uri("/api/v1/indexes/" + indexId + "/versions/1/load"))
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void unload_returns409_whileLoading() {
        UUID indexId = UUID.randomUUID();
        doThrow(new LoadInProgressException("still loading"))
                .when(loadService).unload(indexId, 1);

        assertThat(mvc.delete().uri("/api/v1/indexes/" + indexId + "/versions/1/load"))
                .hasStatus(HttpStatus.CONFLICT);
    }

    @Test
    void getLoaded_doesNotCollideWithGetIndexById() {
        given(loadService.list()).willReturn(List.of());

        assertThat(mvc.get().uri("/api/v1/indexes/loaded"))
                .hasStatus(HttpStatus.OK)
                .bodyJson().extractingPath("$").asList().isEmpty();
    }
}
