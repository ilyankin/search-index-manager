package com.izenkyt.searchindexmanager.indexbuild;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
@EnableConfigurationProperties(IndexBuildProperties.class)
public class IndexBuildAsyncConfig {

    @Bean(name = "buildExecutor", destroyMethod = "shutdown")
    public ThreadPoolTaskExecutor buildExecutor(IndexBuildProperties properties) {
        IndexBuildProperties.Executor cfg = properties.executor();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(cfg.corePoolSize());
        executor.setMaxPoolSize(cfg.maxPoolSize());
        executor.setQueueCapacity(cfg.queueCapacity());
        executor.setThreadNamePrefix(cfg.threadNamePrefix());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationMillis(cfg.awaitTermination().toMillis());
        executor.initialize();
        return executor;
    }
}
