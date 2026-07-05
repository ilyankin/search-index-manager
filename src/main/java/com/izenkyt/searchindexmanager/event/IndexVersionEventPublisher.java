package com.izenkyt.searchindexmanager.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class IndexVersionEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(IndexVersionEventPublisher.class);

    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final IndexEventsProperties properties;

    public IndexVersionEventPublisher(KafkaTemplate<Object, Object> kafkaTemplate,
                                      IndexEventsProperties properties) {
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
    }

    public void publish(IndexVersionUploadedEvent event) {
        String topicKey = event.indexId().toString();
        long timeout = properties.sendTimeout().toSeconds();
        log.debug("Publishing index-version-uploaded event for version {} to topic '{}' (key={}, timeout={}ms)",
                event.versionId(), properties.topic(), topicKey, timeout);
        try {
            kafkaTemplate.send(properties.topic(), topicKey, event)
                    .get(timeout, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            // Таймаут ещё не означает, что публикация не удалась. Из-за ожидания других брокеров
            // Главный брокер мог принять сообщение, но подтверждение не успело прийти от всех других (ask=all)
            throw new EventPublishAmbiguousException("Timed out waiting for ack publishing index-version-uploaded event "
                    + "for version " + event.versionId() + " after " + properties.sendTimeout().toSeconds() + "sec", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EventPublishAmbiguousException("Interrupted while publishing index-version-uploaded event for version "
                    + event.versionId(), e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new EventPublishException("Failed to publish index-version-uploaded event for version "
                    + event.versionId() + ": " + cause.getMessage(), cause);
        }
        log.info("Published index-version-uploaded event for version {} to topic '{}'",
                event.versionId(), properties.topic());
    }
}
