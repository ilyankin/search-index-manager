package com.izenkyt.searchindexmanager.indexbuild;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;

@ConfigurationProperties(prefix = "search.index.build")
public class IndexBuildProperties {

    private String workdir = "./build-work";

    private final Executor executor = new Executor();

    public String getWorkdir() {
        return workdir;
    }

    public void setWorkdir(String workdir) {
        this.workdir = workdir;
    }

    public Path versionDir(UUID indexId, UUID versionId) {
        return Path.of(workdir).resolve(indexId.toString()).resolve(versionId.toString());
    }

    public Executor getExecutor() {
        return executor;
    }

    public static class Executor {

        private int corePoolSize = 1;

        private int maxPoolSize = 2;

        private int queueCapacity = 50;

        private String threadNamePrefix = "build-";

        private Duration awaitTermination = Duration.ofSeconds(25);

        public int getCorePoolSize() {
            return corePoolSize;
        }

        public void setCorePoolSize(int corePoolSize) {
            this.corePoolSize = corePoolSize;
        }

        public int getMaxPoolSize() {
            return maxPoolSize;
        }

        public void setMaxPoolSize(int maxPoolSize) {
            this.maxPoolSize = maxPoolSize;
        }

        public int getQueueCapacity() {
            return queueCapacity;
        }

        public void setQueueCapacity(int queueCapacity) {
            this.queueCapacity = queueCapacity;
        }

        public String getThreadNamePrefix() {
            return threadNamePrefix;
        }

        public void setThreadNamePrefix(String threadNamePrefix) {
            this.threadNamePrefix = threadNamePrefix;
        }

        public Duration getAwaitTermination() {
            return awaitTermination;
        }

        public void setAwaitTermination(Duration awaitTermination) {
            this.awaitTermination = awaitTermination;
        }
    }
}
