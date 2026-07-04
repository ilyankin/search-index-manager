package com.izenkyt.searchindexmanager.indexbuild;

import com.izenkyt.searchindexmanager.common.NotFoundException;
import com.izenkyt.searchindexmanager.index.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class IndexBuildStateStore {

    private static final Logger log = LoggerFactory.getLogger(IndexBuildStateStore.class);

    private final IndexVersionRepository versionRepository;
    private final SearchIndexRepository searchIndexRepository;

    public IndexBuildStateStore(IndexVersionRepository versionRepository,
                                SearchIndexRepository searchIndexRepository) {
        this.versionRepository = versionRepository;
        this.searchIndexRepository = searchIndexRepository;
    }

    @Transactional
    public IndexVersion markIndexVersionAsBuilding(UUID versionId) {
        IndexVersion version = getIndexVersion(versionId);
        version.setStatus(IndexVersionStatus.BUILDING);
        log.debug("Version {} status -> BUILDING", versionId);
        return version;
    }

    @Transactional
    public IndexVersion markIndexVersionAsBuilt(UUID versionId,
                                                 long docCount,
                                                 long artifactSize,
                                                 String checksum) {
        IndexVersion version = getIndexVersion(versionId);
        version.setStatus(IndexVersionStatus.BUILT);
        version.setDocCount(docCount);
        version.setArtifactSize(artifactSize);
        version.setChecksum(checksum);
        log.debug("Version {} status -> BUILT ({} docs, {} bytes)", versionId, docCount, artifactSize);
        return version;
    }

    @Transactional
    public IndexVersion markIndexVersionAsUploaded(UUID versionId, String artifactKey) {
        IndexVersion version = getIndexVersion(versionId);
        version.setStatus(IndexVersionStatus.UPLOADED);
        version.setArtifactKey(artifactKey);
        log.debug("Version {} status -> UPLOADED (artifact={})", versionId, artifactKey);
        return version;
    }

    @Transactional
    public IndexVersion markIndexVersionAsFailed(UUID versionId, String errorMessage) {
        IndexVersion version = getIndexVersion(versionId);
        version.setStatus(IndexVersionStatus.FAILED);
        version.setErrorMessage(errorMessage);
        version.setDocCount(null);
        version.setArtifactSize(null);
        version.setChecksum(null);
        version.setArtifactKey(null);
        log.debug("Version {} status -> FAILED ({})", versionId, errorMessage);
        return version;
    }

    public SearchIndex getIndex(UUID indexId) {
        return searchIndexRepository.findById(indexId)
                .orElseThrow(() -> new NotFoundException("Index " + indexId + " not found"));
    }

    public IndexVersion getIndexVersion(UUID versionId) {
        return versionRepository.findById(versionId)
                .orElseThrow(() -> new NotFoundException("Index version " + versionId + " not found"));
    }
}
