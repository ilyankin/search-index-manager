package com.izenkyt.searchindexmanager.indexbuild;

import com.izenkyt.searchindexmanager.index.api.dto.FieldType;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.index.IndexableField;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentMapperTest {

    private DocumentMapper mapperFor(Map<String, FieldType> schema) {
        Map<String, String> storage = new LinkedHashMap<>();
        schema.forEach((k, v) -> storage.put(k, v.toStorageValue()));
        return new DocumentMapper(storage);
    }

    @Test
    void mapsKeywordToStringFieldStored() {
        DocumentMapper mapper = mapperFor(Map.of("tag", FieldType.KEYWORD));
        Document doc = mapper.toLuceneDocument(Map.of("tag", "java"), 1);

        IndexableField field = doc.getField("tag");
        assertThat(field).isNotNull();
        assertThat(field.stringValue()).isEqualTo("java");
        assertThat(field.fieldType().stored()).isTrue();
    }

    @Test
    void mapsTextToTextFieldStored() {
        DocumentMapper mapper = mapperFor(Map.of("title", FieldType.TEXT));
        Document doc = mapper.toLuceneDocument(Map.of("title", "hello world"), 1);

        IndexableField field = doc.getField("title");
        assertThat(field).isNotNull();
        assertThat(field.stringValue()).isEqualTo("hello world");
        assertThat(field.fieldType().stored()).isTrue();
    }

    @Test
    void mapsLongToLongPointAndStoredField() {
        DocumentMapper mapper = mapperFor(Map.of("count", FieldType.LONG));
        Document doc = mapper.toLuceneDocument(Map.of("count", 42L), 1);

        assertThat(doc.getField("count")).isNotNull();
        StoredField stored = (StoredField) doc.getField("count");
        assertThat(stored.numericValue().longValue()).isEqualTo(42L);
    }

    @Test
    void mapsLongFromNumericString() {
        DocumentMapper mapper = mapperFor(Map.of("count", FieldType.LONG));
        Document doc = mapper.toLuceneDocument(Map.of("count", "15"), 1);

        StoredField stored = (StoredField) doc.getField("count");
        assertThat(stored.numericValue().longValue()).isEqualTo(15L);
    }

    @Test
    void mapsDoubleToDoublePointAndStoredField() {
        DocumentMapper mapper = mapperFor(Map.of("score", FieldType.DOUBLE));
        Document doc = mapper.toLuceneDocument(Map.of("score", 3.14), 1);

        StoredField stored = (StoredField) doc.getField("score");
        assertThat(stored.numericValue().doubleValue()).isEqualTo(3.14);
    }

    @Test
    void mapsBooleanToStringFieldStored() {
        DocumentMapper mapper = mapperFor(Map.of("active", FieldType.BOOLEAN));

        Document trueDoc = mapper.toLuceneDocument(Map.of("active", true), 1);
        assertThat(trueDoc.getField("active").stringValue()).isEqualTo("true");

        Document falseDoc = mapper.toLuceneDocument(Map.of("active", false), 2);
        assertThat(falseDoc.getField("active").stringValue()).isEqualTo("false");
    }

    @Test
    void mapsBooleanFromLowerCaseString() {
        DocumentMapper mapper = mapperFor(Map.of("active", FieldType.BOOLEAN));
        Document doc = mapper.toLuceneDocument(Map.of("active", "TRUE"), 1);
        assertThat(doc.getField("active").stringValue()).isEqualTo("true");
    }

    @Test
    void throwsOnUnknownField_withLineNumber() {
        DocumentMapper mapper = mapperFor(Map.of("title", FieldType.TEXT));

        assertThatThrownBy(() -> mapper.toLuceneDocument(Map.of("bogus", "x"), 7))
                .isInstanceOf(IndexBuildException.class)
                .hasMessageContaining("Line 7")
                .hasMessageContaining("unknown field")
                .hasMessageContaining("bogus");
    }

    @Test
    void throwsOnNullValue() {
        DocumentMapper mapper = mapperFor(Map.of("title", FieldType.TEXT));
        Map<String, Object> row = new HashMap<>();
        row.put("title", null);

        assertThatThrownBy(() -> mapper.toLuceneDocument(row, 3))
                .isInstanceOf(IndexBuildException.class)
                .hasMessageContaining("Line 3")
                .hasMessageContaining("null");
    }

    @Test
    void throwsOnTypeMismatch_forLong() {
        DocumentMapper mapper = mapperFor(Map.of("count", FieldType.LONG));

        assertThatThrownBy(() -> mapper.toLuceneDocument(Map.of("count", "not a number"), 5))
                .isInstanceOf(IndexBuildException.class)
                .hasMessageContaining("Line 5")
                .hasMessageContaining("count")
                .hasMessageContaining("long");
    }

    @Test
    void throwsOnTypeMismatch_forKeyword() {
        DocumentMapper mapper = mapperFor(Map.of("tag", FieldType.KEYWORD));

        assertThatThrownBy(() -> mapper.toLuceneDocument(Map.of("tag", 123), 2))
                .isInstanceOf(IndexBuildException.class)
                .hasMessageContaining("Line 2")
                .hasMessageContaining("keyword");
    }

    @Test
    void throwsOnTypeMismatch_forBoolean() {
        DocumentMapper mapper = mapperFor(Map.of("active", FieldType.BOOLEAN));

        assertThatThrownBy(() -> mapper.toLuceneDocument(Map.of("active", "maybe"), 4))
                .isInstanceOf(IndexBuildException.class)
                .hasMessageContaining("Line 4")
                .hasMessageContaining("boolean");
    }
}
