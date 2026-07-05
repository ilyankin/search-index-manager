package com.izenkyt.searchindexmanager.event;

import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class IndexVersionUploadedEventSerdeTest {

    @Test
    void roundTrip_withoutTypeHeaders_preservesAllFields() {
        IndexVersionUploadedEvent event = new IndexVersionUploadedEvent(
                UUID.randomUUID(), UUID.randomUUID(), 3, "idx/3/index.tar.gz", 456L, "checksum", Instant.now());

        try (JacksonJsonSerializer<IndexVersionUploadedEvent> serializer = new JacksonJsonSerializer<>();
                JacksonJsonDeserializer<IndexVersionUploadedEvent> deserializer =
                        new JacksonJsonDeserializer<>(IndexVersionUploadedEvent.class)) {
            serializer.setAddTypeInfo(false);

            byte[] bytes = serializer.serialize("index-version-uploaded", event);
            IndexVersionUploadedEvent roundTripped = deserializer.deserialize("index-version-uploaded", bytes);

            assertThat(roundTripped).isEqualTo(event);
        }
    }
}
