package com.izenkyt.searchindexmanager.event;

import com.izenkyt.searchindexmanager.indexbuild.IndexBuildStateStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class IndexVersionUploadedListener {

    private static final Logger log = LoggerFactory.getLogger(IndexVersionUploadedListener.class);

    private final IndexBuildStateStore stateStore;

    public IndexVersionUploadedListener(IndexBuildStateStore stateStore) {
        this.stateStore = stateStore;
    }

    @KafkaListener(topics = "${search.index.events.topic}")
    void on(IndexVersionUploadedEvent event) {
        log.debug("Received index-version-uploaded event for version {}", event.versionId());
        stateStore.markIndexVersionAsReady(event.versionId());
    }
}
