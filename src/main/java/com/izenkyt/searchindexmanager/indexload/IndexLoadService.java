package com.izenkyt.searchindexmanager.indexload;

import com.izenkyt.searchindexmanager.common.NotFoundException;
import com.izenkyt.searchindexmanager.index.ArtifactNotAvailableException;
import com.izenkyt.searchindexmanager.index.IndexVersion;
import com.izenkyt.searchindexmanager.index.IndexVersionRepository;
import com.izenkyt.searchindexmanager.index.IndexVersionStatus;
import com.izenkyt.searchindexmanager.index.SearchIndexRepository;
import com.izenkyt.searchindexmanager.indexload.LoadedIndexRegistry.BeginLoadResult;
import com.izenkyt.searchindexmanager.indexload.LoadedIndexRegistry.LoadKey;
import com.izenkyt.searchindexmanager.indexload.LoadedIndexRegistry.LoadedIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class IndexLoadService {

    private static final Logger log = LoggerFactory.getLogger(IndexLoadService.class);

    private final SearchIndexRepository searchIndexRepository;
    private final IndexVersionRepository versionRepository;
    private final LoadedIndexRegistry registry;
    private final IndexLoadPipeline pipeline;
    private final IndexLoadProperties properties;

    public IndexLoadService(SearchIndexRepository searchIndexRepository,
                             IndexVersionRepository versionRepository,
                             LoadedIndexRegistry registry,
                             IndexLoadPipeline pipeline,
                             IndexLoadProperties properties) {
        this.searchIndexRepository = searchIndexRepository;
        this.versionRepository = versionRepository;
        this.registry = registry;
        this.pipeline = pipeline;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public BeginLoadResult startLoad(UUID indexId, int version) {
        IndexVersion indexVersion = versionRepository.findByIndexIdAndVersion(indexId, version)
                .orElseGet(() -> {
                    requireIndexExists(indexId);
                    throw new NotFoundException("Version " + version + " not found for index " + indexId);
                });
        IndexVersionStatus status = indexVersion.getStatus();
        if (!status.hasArtifact()) {
            throw new ArtifactNotAvailableException(
                    "Artifact for version " + version + " of index " + indexId
                            + " is not available (status=" + status + ")");
        }
        LoadKey key = new LoadKey(indexId, version);
        BeginLoadResult result = registry.tryBeginLoad(key);
        if (result.isNew()) {
            log.info("Starting load of index {} version {}", indexId, version);
            pipeline.run(key, indexVersion.getArtifactKey(), indexVersion.getArtifactSize(),
                    indexVersion.getChecksum(), indexVersion.getDocCount());
        }
        return result;
    }

    public List<LoadedIndex> list() {
        return registry.list();
    }

    public void unload(UUID indexId, int version) {
        LoadKey key = new LoadKey(indexId, version);
        registry.unload(key);
        WorkdirCleanup.deleteQuietly(properties.targetDir(indexId, version));
    }

    private void requireIndexExists(UUID id) {
        if (!searchIndexRepository.existsById(id)) {
            throw new NotFoundException("Index " + id + " not found");
        }
    }
}
