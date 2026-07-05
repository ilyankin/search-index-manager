package com.izenkyt.searchindexmanager.indexload;

import com.izenkyt.searchindexmanager.common.NotFoundException;
import org.apache.lucene.index.DirectoryReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class LoadedIndexRegistry {
    private static final Logger log = LoggerFactory.getLogger(LoadedIndexRegistry.class);

    private final Map<LoadKey, LoadedIndex> entries = new ConcurrentHashMap<>();

    public BeginLoadResult tryBeginLoad(LoadKey key) {
        AtomicBoolean created = new AtomicBoolean(false);
        LoadedIndex entry = entries.compute(key, (k, existing) -> {
            if (existing != null && existing.state() != LoadState.FAILED) {
                return existing;
            }
            created.set(true);
            return new LoadedIndex(key);
        });
        if (created.get()) {
            log.debug("Load {} -> LOADING (new attempt)", key);
        }
        return new BeginLoadResult(entry, created.get());
    }

    public void markLoaded(LoadKey key, DirectoryReader reader, long numDocs, long sizeOnDiskBytes) {
        require(key).markLoaded(reader, numDocs, sizeOnDiskBytes);
        log.info("Load {} -> LOADED ({} docs, {} bytes on disk)", key, numDocs, sizeOnDiskBytes);
    }

    public void markFailed(LoadKey key, String message) {
        require(key).markFailed(message);
    }

    public void unload(LoadKey key) {
        LoadedIndex entry = entries.get(key);
        if (entry == null) {
            throw new NotFoundException("Loaded index " + key + " not found");
        }
        if (entry.state() == LoadState.LOADING) {
            throw new LoadInProgressException("Load " + key + " is still in progress");
        }
        entries.remove(key);
        entry.closeReaderQuietly();
        log.debug("Load {} unloaded", key);
    }

    public List<LoadedIndex> list() {
        return List.copyOf(entries.values());
    }

    private LoadedIndex require(LoadKey key) {
        LoadedIndex entry = entries.get(key);
        if (entry == null) {
            throw new IllegalStateException("No registry entry for " + key + "; tryBeginLoad must be called first");
        }
        return entry;
    }

    public record LoadKey(UUID indexId, int version) {
    }

    public enum LoadState {
        LOADING, LOADED, FAILED
    }

    public record BeginLoadResult(LoadedIndex entry, boolean isNew) {
    }

    // closeReaderQuietly() закрывает reader напрямую под синхронизацией этого класса,
    // а не через механизм подсчёта ссылок (reference counting).
    // Если позже появится поисковый API, этот код необходимо заменить на Lucene ReferenceManager/SearcherManager вместо
    // прямого вызова reader.close().
    public static final class LoadedIndex {

        private final LoadKey key;
        private LoadState state;
        private DirectoryReader reader;
        private Long numDocs;
        private Long sizeOnDiskBytes;
        private String error;
        private Instant loadedAt;

        private LoadedIndex(LoadKey key) {
            this.key = key;
            this.state = LoadState.LOADING;
        }

        synchronized void markLoaded(DirectoryReader reader, long numDocs, long sizeOnDiskBytes) {
            this.state = LoadState.LOADED;
            this.reader = reader;
            this.numDocs = numDocs;
            this.sizeOnDiskBytes = sizeOnDiskBytes;
            this.loadedAt = Instant.now();
            this.error = null;
        }

        synchronized void markFailed(String message) {
            this.state = LoadState.FAILED;
            this.error = message;
            this.reader = null;
            this.numDocs = null;
            this.sizeOnDiskBytes = null;
            this.loadedAt = null;
        }

        synchronized void closeReaderQuietly() {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    log.warn("Failed to close reader for {}", key, e);
                }
                reader = null;
            }
        }

        public LoadKey key() {
            return key;
        }

        public synchronized LoadState state() {
            return state;
        }

        public synchronized Long numDocs() {
            return numDocs;
        }

        public synchronized Long sizeOnDiskBytes() {
            return sizeOnDiskBytes;
        }

        public synchronized String error() {
            return error;
        }

        public synchronized Instant loadedAt() {
            return loadedAt;
        }

        // Возвращает согласованный снимок состояния под одной блокировкой, чтобы избежать
        // чтения полей из разных состояний объекта во время конкуренции
        public synchronized Snapshot snapshot() {
            return new Snapshot(state, numDocs, sizeOnDiskBytes, error, loadedAt);
        }
    }

    public record Snapshot(LoadState state, Long numDocs, Long sizeOnDiskBytes, String error, Instant loadedAt) {
    }
}
