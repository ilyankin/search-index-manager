package com.izenkyt.searchindexmanager.indexload;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class IndexLoadPropertiesTest {

    @Test
    void targetDir_composesDirIndexIdAndVersion() {
        IndexLoadProperties properties = new IndexLoadProperties();
        properties.setDir("./load-work");
        UUID indexId = UUID.randomUUID();

        Path result = properties.targetDir(indexId, 3);

        assertThat(result).isEqualTo(Path.of("./load-work").resolve(indexId.toString()).resolve("3"));
    }
}
