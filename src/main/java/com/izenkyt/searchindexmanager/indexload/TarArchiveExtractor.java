package com.izenkyt.searchindexmanager.indexload;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

// Архив поступает из MinIO, поэтому считается недоверенным вводом: объект в бакете
// мог быть изменён вне приложения. При чтении commons-compress не проверяет
// корректность путей внутри архива, поэтому защита от zip-slip и извлечения ссылок (symbolic) реализуется
@Component
public class TarArchiveExtractor {

    private static final Logger log = LoggerFactory.getLogger(TarArchiveExtractor.class);

    // Возвращает общий объём данных, записанных в обычные файлы, чтобы не выполнять
    // повторный обход каталога после распаковки.
    public long extract(Path tarGzFile, Path targetDir) {
        log.debug("Extracting {} into {}", tarGzFile, targetDir);
        try {
            Files.createDirectories(targetDir);
        } catch (IOException e) {
            throw new IndexLoadException("Failed to create extraction directory " + targetDir + ": " + e.getMessage(), e);
        }
        long totalBytes = 0;
        try (InputStream fis = Files.newInputStream(tarGzFile);
             BufferedInputStream bis = new BufferedInputStream(fis);
             GzipCompressorInputStream gz = new GzipCompressorInputStream(bis);
             TarArchiveInputStream tar = new TarArchiveInputStream(gz)) {
            TarArchiveEntry entry;
            while ((entry = tar.getNextEntry()) != null) {
                totalBytes += extractEntry(tar, entry, targetDir);
            }
        } catch (IOException e) {
            throw new IndexLoadException("Failed to extract archive " + tarGzFile + ": " + e.getMessage(), e);
        }
        log.debug("Extracted {} into {} ({} bytes)", tarGzFile, targetDir, totalBytes);
        return totalBytes;
    }

    private long extractEntry(TarArchiveInputStream tar, TarArchiveEntry entry, Path targetDir) throws IOException {
        // TarArchiveEntry.isFile() возвращает true и для символических ссылок, поэтому isSymbolicLink()/isLink()
        // необходимо проверять отдельно и до проверки isFile()/isDirectory()
        if (entry.isSymbolicLink() || entry.isLink()) {
            throw new IndexLoadException("Refusing to extract link tar entry '" + entry.getName() + "'");
        }
        Path resolved = resolveSafely(targetDir, entry.getName());
        if (entry.isDirectory()) {
            Files.createDirectories(resolved);
            return 0;
        } else if (entry.isFile()) {
            Files.createDirectories(resolved.getParent());
            return Files.copy(tar, resolved, StandardCopyOption.REPLACE_EXISTING);
        } else {
            throw new IndexLoadException("Refusing to extract unsupported tar entry '" + entry.getName() + "'");
        }
    }

    private Path resolveSafely(Path targetDir, String entryName) {
        // targetDir тоже должен быть нормализован: если он содержит ненормализованный
        // относительный путь (например, "./load-work/..."), а вычисленный путь уже
        // нормализован, то startsWith() ошибочно будет возвращать false для каждой записи
        Path normalizedTarget = targetDir.normalize();
        Path resolved = normalizedTarget.resolve(entryName).normalize();
        if (!resolved.startsWith(normalizedTarget)) {
            throw new IndexLoadException("Refusing to extract tar entry outside target directory: '" + entryName + "'");
        }
        return resolved;
    }
}
