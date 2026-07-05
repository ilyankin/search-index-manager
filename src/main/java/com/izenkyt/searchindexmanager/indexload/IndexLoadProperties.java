package com.izenkyt.searchindexmanager.indexload;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.util.UUID;

@ConfigurationProperties(prefix = "search.index.load")
public class IndexLoadProperties {

    private String dir = "./load-work";

    private final Executor executor = new Executor();

    public String getDir() {
        return dir;
    }

    public void setDir(String dir) {
        this.dir = dir;
    }

    public Path targetDir(UUID indexId, int version) {
        return Path.of(dir).resolve(indexId.toString()).resolve(String.valueOf(version));
    }

    public Executor getExecutor() {
        return executor;
    }

    public static class Executor {

        private int corePoolSize = 1;

        private int maxPoolSize = 2;

        private int queueCapacity = 50;

        private String threadNamePrefix = "load-";

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
    }
}
