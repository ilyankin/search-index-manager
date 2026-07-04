package com.izenkyt.searchindexmanager.index;

import com.izenkyt.searchindexmanager.common.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "search_index")
public class SearchIndex extends AuditableEntity {

    @Id
    @GeneratedValue(generator = "search_index_id")
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false, unique = true)
    private String name;

    @Column(name = "description")
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "fields", nullable = false, columnDefinition = "jsonb")
    private Map<String, String> fields = new LinkedHashMap<>();

    protected SearchIndex() {
    }

    public SearchIndex(String name, String description, Map<String, String> fields) {
        this.name = name;
        this.description = description;
        this.fields = fields == null ? new LinkedHashMap<>() : new LinkedHashMap<>(fields);
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Map<String, String> getFields() {
        return fields;
    }

    public void setFields(Map<String, String> fields) {
        this.fields = fields;
    }
}