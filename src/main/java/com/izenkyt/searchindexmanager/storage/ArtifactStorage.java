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

    private final MinioClient client;
    private final MinioClient presignClient;
    private final MinioStorageProperties properties;

    public ArtifactStorage(@Qualifier("minioClient") MinioClient client,
                            @Qualifier("minioPresignClient") MinioClient presignClient,
                            MinioStorageProperties properties) {
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
        log.debug("Uploading artifact {} ({} bytes) to bucket '{}'", key, size, properties.bucket());
        try (InputStream in = Files.newInputStream(file)) {
            client.putObject(PutObjectArgs.builder()
                    .bucket(properties.bucket())
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
                    .bucket(properties.bucket())
                    .object(key)
                    .build());
        } catch (Exception e) {
            throw new ArtifactStorageException("Failed to delete artifact '" + key + "': " + e.getMessage(), e);
        }
        log.debug("Deleted artifact {}", key);
    }

    public PresignedUrl presignDownload(String key) {
        log.debug("Presigning download URL for {} (ttl={})", key, properties.presignTtl());
        try {
            String url = presignClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Http.Method.GET)
                    .bucket(properties.bucket())
                    .object(key)
                    .expiry((int) properties.presignTtl().toSeconds(), TimeUnit.SECONDS)
                    .build()
            );
            Instant expiresAt = Instant.now().plus(properties.presignTtl());
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
                    .bucket(properties.bucket())
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
