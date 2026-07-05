package com.izenkyt.searchindexmanager.indexload;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.FileSystemUtils;

import java.io.IOException;
import java.nio.file.Path;

final class WorkdirCleanup {

    private static final Logger log = LoggerFactory.getLogger(WorkdirCleanup.class);

    private WorkdirCleanup() {
    }

    static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            FileSystemUtils.deleteRecursively(path);
        } catch (IOException e) {
            log.warn("Failed to delete {}", path, e);
        }
    }
}
