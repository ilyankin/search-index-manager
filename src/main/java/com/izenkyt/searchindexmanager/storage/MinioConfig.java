package com.izenkyt.searchindexmanager.storage;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(MinioStorageProperties.class)
public class MinioConfig {

    private static final Logger log = LoggerFactory.getLogger(MinioConfig.class);

    @Bean
    public MinioClient minioClient(MinioStorageProperties properties) {
        return MinioClient.builder()
                .endpoint(properties.getEndpoint())
                .credentials(properties.getAccessKey(), properties.getSecretKey())
                .build();
    }

    // Для presigned URL используется отдельный MinIOClient с публичным endpoint.
    // Хост endpoint'а является частью подписи, поэтому ссылка должна сразу
    // подписываться для адреса, доступного клиентам. Регион задаётся явно, чтобы
    // исключить вызов GetBucketLocation, поскольку публичный endpoint может быть
    // недоступен самому приложению.
    @Bean
    public MinioClient minioPresignClient(MinioStorageProperties properties) {
        return MinioClient.builder()
                .endpoint(properties.getPublicEndpoint())
                .credentials(properties.getAccessKey(), properties.getSecretKey())
                .region("us-east-1") // default
                .build();
    }

    @Bean
    ApplicationRunner ensureMinioBucket(@Qualifier("minioClient") MinioClient minioClient,
                                         MinioStorageProperties properties) {
        return _ -> {
            String bucket = properties.getBucket();
            try {
                if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                    minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                    log.info("Created MinIO bucket '{}'", bucket);
                }
            } catch (Exception e) {
                log.warn("Failed to ensure MinIO bucket '{}' at startup; artifact uploads/downloads will fail "
                        + "until it exists", bucket, e);
            }
        };
    }
}
