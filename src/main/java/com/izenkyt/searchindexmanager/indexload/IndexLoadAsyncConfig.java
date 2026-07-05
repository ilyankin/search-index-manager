package com.izenkyt.searchindexmanager.indexload;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.util.FileSystemUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Configuration
@EnableAsync
@EnableConfigurationProperties(IndexLoadProperties.class)
public class IndexLoadAsyncConfig {

    private static final Logger log = LoggerFactory.getLogger(IndexLoadAsyncConfig.class);

    @Bean(name = "loadExecutor", destroyMethod = "shutdown")
    public ThreadPoolTaskExecutor loadExecutor(IndexLoadProperties properties) {
        IndexLoadProperties.Executor cfg = properties.getExecutor();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(cfg.getCorePoolSize());
        executor.setMaxPoolSize(cfg.getMaxPoolSize());
        executor.setQueueCapacity(cfg.getQueueCapacity());
        executor.setThreadNamePrefix(cfg.getThreadNamePrefix());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationMillis(cfg.getAwaitTermination().toMillis());
        executor.initialize();
        return executor;
    }

    @Bean
    ApplicationRunner clearLoadWorkdir(IndexLoadProperties properties) {
        return _ -> {
            Path dir = Path.of(properties.getDir());
            try {
                FileSystemUtils.deleteRecursively(dir);
                Files.createDirectories(dir);
            } catch (IOException e) {
                log.warn("Failed to clear load workdir {} at startup", dir, e);
            }
        };
    }
}
