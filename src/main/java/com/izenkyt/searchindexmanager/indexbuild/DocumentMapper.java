package com.izenkyt.searchindexmanager.indexbuild;

import com.izenkyt.searchindexmanager.index.api.dto.FieldType;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.DoublePoint;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;

import java.util.LinkedHashMap;
import java.util.Map;

public class DocumentMapper {

    private final Map<String, FieldType> schema;

    public DocumentMapper(Map<String, String> schema) {
        this.schema = new LinkedHashMap<>();
        if (schema != null) {
            schema.forEach((name, value) -> this.schema.put(name, FieldType.fromStorageValue(value)));
        }
    }

    public Document toLuceneDocument(Map<String, Object> row, int lineNumber) {
        Document doc = new Document();
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            String fieldName = entry.getKey();
            FieldType type = schema.get(fieldName);
            if (type == null) {
                throw new IndexBuildException(
                        "Line " + lineNumber + ": unknown field '" + fieldName + "'");
            }
            Object value = entry.getValue();
            if (value == null) {
                throw new IndexBuildException(
                        "Line " + lineNumber + ": field '" + fieldName + "' is null");
            }
            addField(doc, fieldName, type, value, lineNumber);
        }
        return doc;
    }

    private void addField(Document doc, String fieldName, FieldType type, Object value, int lineNumber) {
        switch (type) {
            case KEYWORD -> {
                if (!(value instanceof String s)) {
                    throw typeMismatch(fieldName, "keyword (string)", value, lineNumber);
                }
                doc.add(new StringField(fieldName, s, Field.Store.YES));
            }
            case TEXT -> {
                if (!(value instanceof String s)) {
                    throw typeMismatch(fieldName, "text (string)", value, lineNumber);
                }
                doc.add(new Field(fieldName, s, TextField.TYPE_STORED));
            }
            case LONG -> {
                long lv = toLong(value, fieldName, lineNumber);
                doc.add(new StoredField(fieldName, lv));
                doc.add(new LongPoint(fieldName, lv));
            }
            case DOUBLE -> {
                double dv = toDouble(value, fieldName, lineNumber);
                doc.add(new StoredField(fieldName, dv));
                doc.add(new DoublePoint(fieldName, dv));
            }
            case BOOLEAN -> {
                boolean bv = toBoolean(value, fieldName, lineNumber);
                doc.add(new StringField(fieldName, Boolean.toString(bv), Field.Store.YES));
            }
        }
    }

    private long toLong(Object value, String fieldName, int lineNumber) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        if (value instanceof String s) {
            try {
                return Long.parseLong(s.trim());
            } catch (NumberFormatException e) {
                throw typeMismatch(fieldName, "long", value, lineNumber);
            }
        }
        throw typeMismatch(fieldName, "long", value, lineNumber);
    }

    private double toDouble(Object value, String fieldName, int lineNumber) {
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        if (value instanceof String s) {
            try {
                return Double.parseDouble(s.trim());
            } catch (NumberFormatException e) {
                throw typeMismatch(fieldName, "double", value, lineNumber);
            }
        }
        throw typeMismatch(fieldName, "double", value, lineNumber);
    }

    private boolean toBoolean(Object value, String fieldName, int lineNumber) {
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof String s) {
            String t = s.trim();
            if ("true".equalsIgnoreCase(t)) {
                return true;
            }
            if ("false".equalsIgnoreCase(t)) {
                return false;
            }
        }
        throw typeMismatch(fieldName, "boolean", value, lineNumber);
    }

    private IndexBuildException typeMismatch(String fieldName, String expected, Object value, int lineNumber) {
        return new IndexBuildException("Line " + lineNumber + ": field '" + fieldName
                + "' expected " + expected + " but got " + value.getClass().getSimpleName()
                + " value '" + value + "'");
    }
}
