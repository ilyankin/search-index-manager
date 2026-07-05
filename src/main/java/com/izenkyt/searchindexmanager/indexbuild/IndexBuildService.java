package com.izenkyt.searchindexmanager.indexbuild;

import com.izenkyt.searchindexmanager.common.NotFoundException;
import com.izenkyt.searchindexmanager.index.IndexVersion;
import com.izenkyt.searchindexmanager.index.IndexVersionRepository;
import com.izenkyt.searchindexmanager.index.IndexVersionStatus;
import com.izenkyt.searchindexmanager.index.SearchIndex;
import com.izenkyt.searchindexmanager.index.SearchIndexRepository;
import com.izenkyt.searchindexmanager.index.api.dto.IndexVersionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Service
public class IndexBuildService {

    private static final Logger log = LoggerFactory.getLogger(IndexBuildService.class);

    private final SearchIndexRepository searchIndexRepository;
    private final IndexVersionRepository versionRepository;
    private final IndexBuildPipeline pipeline;
    private final IndexBuildStateStore stateStore;
    private final IndexBuildProperties properties;

    public IndexBuildService(SearchIndexRepository searchIndexRepository,
                             IndexVersionRepository versionRepository,
                             IndexBuildPipeline pipeline,
                             IndexBuildStateStore stateStore,
                             IndexBuildProperties properties) {
        this.searchIndexRepository = searchIndexRepository;
        this.versionRepository = versionRepository;
        this.pipeline = pipeline;
        this.stateStore = stateStore;
        this.properties = properties;
    }

    @Transactional
    public IndexVersionResponse startBuild(UUID indexId, InputStream ndjson) {
        log.debug("Starting build for index {}", indexId);
        UUID versionId = createVersion(indexId);
        Path ndjsonFile = resolveNdjsonFile(indexId, versionId);
        try {
            streamToFile(ndjson, ndjsonFile);
            log.debug("Streamed NDJSON for version {} to {}", versionId, ndjsonFile);
        } catch (IOException e) {
            stateStore.markIndexVersionAsFailed(versionId, "Failed to read uploaded documents: " + e.getMessage());
            throw new IndexBuildException("Failed to read uploaded documents", e);
        }
        // Запускаем pipeline.run() только после коммита: он асинхронный и читает версию в своей
        // транзакции на отдельном потоке/соединении — если запустить его прямо здесь, до коммита
        // текущей транзакции, под READ_COMMITTED он иногда не увидит ещё не закоммиченную строку
        // версии (гонка, "Index version ... not found").
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                pipeline.run(versionId, ndjsonFile);
            }
        });
        return toVersionResponse(stateStore.getIndexVersion(versionId));
    }


    public UUID createVersion(UUID indexId) {
        SearchIndex index = searchIndexRepository.findById(indexId)
                .orElseThrow(() -> new NotFoundException("Index " + indexId + " not found"));
        if (versionRepository.hasActiveBuild(indexId)) {
            throw new IndexBuildConflictException("Index " + indexId + " already has an active build");
        }
        int nextVersion = versionRepository.findMaxVersionByIndexId(indexId).orElse(0) + 1;
        log.debug("Creating version {} for index '{}'", nextVersion, index.getName());
        IndexVersion version = new IndexVersion(index, nextVersion, IndexVersionStatus.CREATED);
        try {
            return versionRepository.saveAndFlush(version).getId();
        } catch (DataIntegrityViolationException e) {
            throw new IndexBuildConflictException("Index " + indexId + " already has an active build");
        }
    }

    private Path resolveNdjsonFile(UUID indexId, UUID versionId) {
        return properties.versionDir(indexId, versionId).resolve("documents.ndjson");
    }

    private void streamToFile(InputStream in, Path target) throws IOException {
        Files.createDirectories(target.getParent());
        try (OutputStream out = Files.newOutputStream(target)) {
            in.transferTo(out);
        }
    }

    private IndexVersionResponse toVersionResponse(IndexVersion version) {
        return new IndexVersionResponse(
                version.getId(),
                version.getVersion(),
                version.getStatus().name(),
                version.getDocCount(),
                version.getArtifactKey(),
                version.getArtifactSize(),
                version.getChecksum(),
                version.getErrorMessage(),
                version.getCreatedAt(),
                version.getUpdatedAt()
        );
    }
}
