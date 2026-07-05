package com.izenkyt.searchindexmanager.storage;

import io.minio.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Component
public class ArtifactStorage {

    private static final Logger log = LoggerFactory.getLogger(ArtifactStorage.class);
    private static final String CONTENT_TYPE = "application/gzip";
    private static final Duration MAX_PRESIGN_TTL = Duration.ofDays(7);

    private final MinioClient client;
    private final MinioClient presignClient;
    private final MinioStorageProperties properties;

    public ArtifactStorage(@Qualifier("minioClient") MinioClient client,
                            @Qualifier("minioPresignClient") MinioClient presignClient,
                            MinioStorageProperties properties) {
        Duration ttl = properties.getPresignTtl();
        if (ttl.isZero() || ttl.isNegative() || ttl.compareTo(MAX_PRESIGN_TTL) > 0) {
            throw new IllegalStateException(
                    "search.index.storage.presign-ttl must be between 1s and " + MAX_PRESIGN_TTL
                            + ", got " + ttl);
        }
        this.client = client;
        this.presignClient = presignClient;
        this.properties = properties;
    }

    public void upload(String key, Path file) {
        long size;
        try {
            size = Files.size(file);
        } catch (IOException e) {
            throw new ArtifactStorageException("Failed to read artifact size for '" + key + "': " + e.getMessage(), e);
        }
        log.debug("Uploading artifact {} ({} bytes) to bucket '{}'", key, size, properties.getBucket());
        try (InputStream in = Files.newInputStream(file)) {
            client.putObject(PutObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(key)
                    .stream(in, size, -1L)
                    .contentType(CONTENT_TYPE)
                    .build());
        } catch (Exception e) {
            throw new ArtifactStorageException("Failed to upload artifact '" + key + "': " + e.getMessage(), e);
        }
        log.debug("Uploaded artifact {}", key);
    }

    public void delete(String key) {
        log.debug("Deleting artifact {}", key);
        try {
            client.removeObject(RemoveObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(key)
                    .build());
        } catch (Exception e) {
            throw new ArtifactStorageException("Failed to delete artifact '" + key + "': " + e.getMessage(), e);
        }
        log.debug("Deleted artifact {}", key);
    }

    public PresignedUrl presignDownload(String key) {
        log.debug("Presigning download URL for {} (ttl={})", key, properties.getPresignTtl());
        try {
            String url = presignClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Http.Method.GET)
                    .bucket(properties.getBucket())
                    .object(key)
                    .expiry((int) properties.getPresignTtl().toSeconds(), TimeUnit.SECONDS)
                    .build()
            );
            Instant expiresAt = Instant.now().plus(properties.getPresignTtl());
            log.debug("Presigned URL for {} expires at {}", key, expiresAt);
            return new PresignedUrl(url, expiresAt);
        } catch (Exception e) {
            throw new ArtifactStorageException("Failed to presign download URL for '" + key + "': " + e.getMessage(), e);
        }
    }

    public void downloadTo(String key, Path target) {
        log.debug("Downloading artifact {} to {}", key, target);
        try {
            Files.createDirectories(target.getParent());
            try (InputStream in = client.getObject(GetObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(key)
                    .build())) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            throw new ArtifactStorageException("Failed to download artifact '" + key + "': " + e.getMessage(), e);
        }
        log.debug("Downloaded artifact {} to {}", key, target);
    }

    public record PresignedUrl(String url, Instant expiresAt) {
    }
}
