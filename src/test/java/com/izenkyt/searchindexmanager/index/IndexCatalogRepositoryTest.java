package com.izenkyt.searchindexmanager.index;

import com.izenkyt.searchindexmanager.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.springframework.dao.DataIntegrityViolationException;

@SpringBootTest
@ContextConfiguration(classes = TestcontainersConfiguration.class)
@Transactional
class IndexCatalogRepositoryTest {

    @Autowired
    private SearchIndexRepository searchIndexRepository;

    @Autowired
    private IndexVersionRepository indexVersionRepository;

    private SearchIndex newIndex(String name, Map<String, String> fields) {
        return new SearchIndex(name, "desc for " + name, fields);
    }

    private SearchIndex save(String name) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("title", "text");
        fields.put("count", "long");
        return searchIndexRepository.save(newIndex(name, fields));
    }

    @Test
    void searchIndexFieldsJsonbRoundTripSurvives() {
        SearchIndex saved = save("idx-fields");
        searchIndexRepository.flush();

        SearchIndex loaded = searchIndexRepository.findById(saved.getId()).orElseThrow();

        assertThat(loaded.getFields()).containsEntry("title", "text").containsEntry("count", "long").hasSize(2);
        assertThat(loaded.getName()).isEqualTo("idx-fields");
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(loaded.getUpdatedAt()).isNotNull();
    }

    @Test
    void searchIndexIdIsVersion7Uuid() {
        SearchIndex saved = save("idx-uuid7");
        searchIndexRepository.flush();

        UUID id = saved.getId();

        assertThat(id).isNotNull();
        assertThat(id.version()).as("UUID version field should be 7 for UUIDv7").isEqualTo(7);
    }

    @Test
    void searchIndexNameMustBeUnique() {
        save("idx-unique");
        searchIndexRepository.flush();

        searchIndexRepository.save(newIndex("idx-unique", Map.of("t", "text")));
        assertThatThrownBy(searchIndexRepository::flush)
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void indexVersionIsPersistedAndOrderedByVersionDesc() {
        SearchIndex index = save("idx-versions");

        indexVersionRepository.save(new IndexVersion(index, 1, IndexVersionStatus.CREATED));
        indexVersionRepository.save(new IndexVersion(index, 2, IndexVersionStatus.BUILDING));
        indexVersionRepository.flush();

        List<IndexVersion> versions = indexVersionRepository.findAllByIndexIdOrderByVersionDesc(index.getId());

        assertThat(versions).extracting(IndexVersion::getVersion).containsExactly(2, 1);
    }

    @Test
    void findMaxVersionByIndexIdReturnsLatest() {
        SearchIndex index = save("idx-max");

        indexVersionRepository.save(new IndexVersion(index, 1, IndexVersionStatus.CREATED));
        indexVersionRepository.save(new IndexVersion(index, 3, IndexVersionStatus.BUILDING));
        indexVersionRepository.save(new IndexVersion(index, 2, IndexVersionStatus.BUILT));
        indexVersionRepository.flush();

        Optional<Integer> max = indexVersionRepository.findMaxVersionByIndexId(index.getId());

        assertThat(max).hasValue(3);
    }

    @Test
    void findMaxVersionByIndexIdIsEmptyWhenNoVersions() {
        SearchIndex index = save("idx-noversion");

        Optional<Integer> max = indexVersionRepository.findMaxVersionByIndexId(index.getId());

        assertThat(max).isEmpty();
    }

    @Test
    void indexIdAndVersionMustBeUnique() {
        SearchIndex index = save("idx-version-unique");

        indexVersionRepository.save(new IndexVersion(index, 1, IndexVersionStatus.CREATED));
        indexVersionRepository.save(new IndexVersion(index, 1, IndexVersionStatus.BUILT));
        assertThatThrownBy(indexVersionRepository::flush)
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findByNameReturnsIndex() {
        save("idx-by-name");
        searchIndexRepository.flush();

        assertThat(searchIndexRepository.existsByName("idx-by-name")).isTrue();
        assertThat(searchIndexRepository.findByName("idx-by-name")).isPresent();
        assertThat(searchIndexRepository.findByName("missing")).isEmpty();
    }

    @Test
    void deletingIndexCascadesToVersionsViaDb() {
        SearchIndex index = save("idx-cascade");
        indexVersionRepository.save(new IndexVersion(index, 1, IndexVersionStatus.CREATED));
        indexVersionRepository.flush();

        UUID indexId = index.getId();
        searchIndexRepository.delete(index);
        searchIndexRepository.flush();

        assertThat(searchIndexRepository.findById(indexId)).isEmpty();
    }
}