package com.izenkyt.searchindexmanager.index;

import com.izenkyt.searchindexmanager.common.DuplicateNameException;
import com.izenkyt.searchindexmanager.common.NotFoundException;
import com.izenkyt.searchindexmanager.index.api.dto.ArtifactDownloadResponse;
import com.izenkyt.searchindexmanager.index.api.dto.CreateIndexRequest;
import com.izenkyt.searchindexmanager.index.api.dto.FieldDefinition;
import com.izenkyt.searchindexmanager.index.api.dto.FieldType;
import com.izenkyt.searchindexmanager.index.api.dto.IndexResponse;
import com.izenkyt.searchindexmanager.index.api.dto.IndexVersionResponse;
import com.izenkyt.searchindexmanager.storage.ArtifactStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class IndexCatalogServiceTest {

    @Mock
    private SearchIndexRepository searchIndexRepository;

    @Mock
    private IndexVersionRepository indexVersionRepository;

    @Mock
    private ArtifactStorage artifactStorage;

    @InjectMocks
    private IndexCatalogService service;

    private CreateIndexRequest request(String name) {
        return new CreateIndexRequest(
                name,
                "description for " + name,
                List.of(new FieldDefinition("title", FieldType.TEXT),
                        new FieldDefinition("count", FieldType.LONG))
        );
    }

    @Test
    void create_returnsResponseWithNameAndFields_andChecksDuplicate() {
        given(searchIndexRepository.existsByName("idx")).willReturn(false);
        given(searchIndexRepository.save(any(SearchIndex.class)))
                .willAnswer(inv -> inv.getArgument(0));

        IndexResponse response = service.create(request("idx"));

        assertThat(response.name()).isEqualTo("idx");
        assertThat(response.description()).isEqualTo("description for idx");
        assertThat(response.fields()).hasSize(2);
        assertThat(response.fields()).extracting(FieldDefinition::name).contains("title", "count");
        assertThat(response.fields()).extracting(FieldDefinition::type).contains(FieldType.TEXT, FieldType.LONG);
        verify(searchIndexRepository).existsByName("idx");
        verify(searchIndexRepository).save(any(SearchIndex.class));
    }

    @Test
    void create_throwsDuplicate_whenNameExists() {
        given(searchIndexRepository.existsByName("idx")).willReturn(true);

        assertThatThrownBy(() -> service.create(request("idx")))
                .isInstanceOf(DuplicateNameException.class)
                .hasMessageContaining("idx");

        verify(searchIndexRepository, never()).save(any(SearchIndex.class));
    }

    @Test
    void getById_throwsNotFound_whenMissing() {
        UUID id = UUID.randomUUID();
        given(searchIndexRepository.findById(id)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(id))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining(id.toString());
    }

    @Test
    void getById_returnsResponse_whenFound() {
        UUID id = UUID.randomUUID();
        SearchIndex index = new SearchIndex("idx", "desc", Map.of("title", "text"));
        given(searchIndexRepository.findById(id)).willReturn(Optional.of(index));

        IndexResponse response = service.getById(id);

        assertThat(response.name()).isEqualTo("idx");
        assertThat(response.fields()).hasSize(1);
    }

    @Test
    void list_returnsPageOfResponses() {
        SearchIndex index = new SearchIndex("a", "d", Map.of("t", "text"));
        Page<SearchIndex> page = new PageImpl<>(List.of(index));
        given(searchIndexRepository.findAll(any(Pageable.class))).willReturn(page);

        Page<IndexResponse> result = service.list(PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).name()).isEqualTo("a");
    }

    @Test
    void listVersions_throwsNotFound_whenIndexMissing() {
        UUID id = UUID.randomUUID();
        given(searchIndexRepository.existsById(id)).willReturn(false);

        assertThatThrownBy(() -> service.listVersions(id))
                .isInstanceOf(NotFoundException.class);
        verify(indexVersionRepository, never()).findAllByIndexIdOrderByVersionDesc(any());
    }

    @Test
    void listVersions_returnsOrderedResponses_whenIndexExists() {
        UUID id = UUID.randomUUID();
        SearchIndex index = new SearchIndex("idx", "d", Map.of("t", "text"));
        given(searchIndexRepository.existsById(id)).willReturn(true);
        IndexVersion v2 = new IndexVersion(index, 2, IndexVersionStatus.BUILT);
        IndexVersion v1 = new IndexVersion(index, 1, IndexVersionStatus.CREATED);
        given(indexVersionRepository.findAllByIndexIdOrderByVersionDesc(id)).willReturn(List.of(v2, v1));

        List<IndexVersionResponse> result = service.listVersions(id);

        assertThat(result).extracting(IndexVersionResponse::version).containsExactly(2, 1);
        assertThat(result).extracting(IndexVersionResponse::status).contains("BUILT", "CREATED");
    }

    @Test
    void getVersion_throwsNotFound_whenIndexMissing() {
        UUID id = UUID.randomUUID();
        given(indexVersionRepository.findByIndexIdAndVersion(id, 1)).willReturn(Optional.empty());
        given(searchIndexRepository.existsById(id)).willReturn(false);

        assertThatThrownBy(() -> service.getVersion(id, 1))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Index " + id);
    }

    @Test
    void getVersion_throwsNotFound_whenVersionMissing() {
        UUID id = UUID.randomUUID();
        given(indexVersionRepository.findByIndexIdAndVersion(id, 1)).willReturn(Optional.empty());
        given(searchIndexRepository.existsById(id)).willReturn(true);

        assertThatThrownBy(() -> service.getVersion(id, 1))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Version 1");
    }

    @Test
    void getVersion_returnsResponse_whenFound() {
        UUID id = UUID.randomUUID();
        SearchIndex index = new SearchIndex("idx", "d", Map.of("t", "text"));
        IndexVersion v = new IndexVersion(index, 3, IndexVersionStatus.BUILT);
        v.setDocCount(42L);
        given(indexVersionRepository.findByIndexIdAndVersion(id, 3)).willReturn(Optional.of(v));

        IndexVersionResponse response = service.getVersion(id, 3);

        assertThat(response.version()).isEqualTo(3);
        assertThat(response.status()).isEqualTo("BUILT");
        assertThat(response.docCount()).isEqualTo(42L);
    }

    @Test
    void getArtifactDownloadUrl_returnsUrl_whenUploaded() {
        UUID id = UUID.randomUUID();
        SearchIndex index = new SearchIndex("idx", "d", Map.of("t", "text"));
        IndexVersion v = new IndexVersion(index, 1, IndexVersionStatus.UPLOADED);
        v.setArtifactKey(id + "/1/index.tar.gz");
        given(indexVersionRepository.findByIndexIdAndVersion(id, 1)).willReturn(Optional.of(v));
        Instant expiresAt = Instant.parse("2026-07-04T09:15:00Z");
        given(artifactStorage.presignDownload(id + "/1/index.tar.gz"))
                .willReturn(new ArtifactStorage.PresignedUrl("http://localhost:9000/idx/1/index.tar.gz", expiresAt));

        ArtifactDownloadResponse response = service.getArtifactDownloadUrl(id, 1);

        assertThat(response.url()).startsWith("http://localhost:9000");
        assertThat(response.expiresAt()).isEqualTo(expiresAt);
    }

    @Test
    void getArtifactDownloadUrl_returnsUrl_whenReady() {
        UUID id = UUID.randomUUID();
        SearchIndex index = new SearchIndex("idx", "d", Map.of("t", "text"));
        IndexVersion v = new IndexVersion(index, 1, IndexVersionStatus.READY);
        v.setArtifactKey(id + "/1/index.tar.gz");
        given(indexVersionRepository.findByIndexIdAndVersion(id, 1)).willReturn(Optional.of(v));
        given(artifactStorage.presignDownload(any(String.class)))
                .willReturn(new ArtifactStorage.PresignedUrl("http://x", Instant.now()));

        service.getArtifactDownloadUrl(id, 1);

        verify(artifactStorage).presignDownload(id + "/1/index.tar.gz");
    }

    @Test
    void getArtifactDownloadUrl_throws409_whenStatusBuilding() {
        UUID id = UUID.randomUUID();
        SearchIndex index = new SearchIndex("idx", "d", Map.of("t", "text"));
        IndexVersion v = new IndexVersion(index, 1, IndexVersionStatus.BUILDING);
        given(indexVersionRepository.findByIndexIdAndVersion(id, 1)).willReturn(Optional.of(v));

        assertThatThrownBy(() -> service.getArtifactDownloadUrl(id, 1))
                .isInstanceOf(ArtifactNotAvailableException.class)
                .hasMessageContaining("BUILDING");
        verify(artifactStorage, never()).presignDownload(any(String.class));
    }

    @Test
    void getArtifactDownloadUrl_throwsNotFound_whenVersionMissing() {
        UUID id = UUID.randomUUID();
        given(indexVersionRepository.findByIndexIdAndVersion(id, 1)).willReturn(Optional.empty());
        given(searchIndexRepository.existsById(id)).willReturn(true);

        assertThatThrownBy(() -> service.getArtifactDownloadUrl(id, 1))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Version 1");
    }
}