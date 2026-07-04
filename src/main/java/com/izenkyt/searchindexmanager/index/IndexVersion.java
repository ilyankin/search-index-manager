package com.izenkyt.searchindexmanager.index;

import com.izenkyt.searchindexmanager.common.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

@Entity
@Table(
        name = "index_version",
        uniqueConstraints = @UniqueConstraint(name = "uk_index_version_index_id_version", columnNames = {"index_id", "version"})
)
public class IndexVersion extends AuditableEntity {

    @Id
    @GeneratedValue(generator = "index_version_id")
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "index_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private SearchIndex index;

    @Column(name = "version", nullable = false)
    private int version;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private IndexVersionStatus status;

    @Column(name = "doc_count")
    private Long docCount;

    @Column(name = "artifact_key")
    private String artifactKey;

    @Column(name = "artifact_size")
    private Long artifactSize;

    @Column(name = "checksum")
    private String checksum;

    @Column(name = "error_message")
    private String errorMessage;

    protected IndexVersion() {
    }

    public IndexVersion(SearchIndex index, int version, IndexVersionStatus status) {
        this.index = index;
        this.version = version;
        this.status = status;
    }

    public UUID getId() {
        return id;
    }

    public SearchIndex getIndex() {
        return index;
    }

    public void setIndex(SearchIndex index) {
        this.index = index;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public IndexVersionStatus getStatus() {
        return status;
    }

    public void setStatus(IndexVersionStatus status) {
        this.status = status;
    }

    public Long getDocCount() {
        return docCount;
    }

    public void setDocCount(Long docCount) {
        this.docCount = docCount;
    }

    public String getArtifactKey() {
        return artifactKey;
    }

    public void setArtifactKey(String artifactKey) {
        this.artifactKey = artifactKey;
    }

    public Long getArtifactSize() {
        return artifactSize;
    }

    public void setArtifactSize(Long artifactSize) {
        this.artifactSize = artifactSize;
    }

    public String getChecksum() {
        return checksum;
    }

    public void setChecksum(String checksum) {
        this.checksum = checksum;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}