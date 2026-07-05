package com.izenkyt.searchindexmanager.event;

import com.izenkyt.searchindexmanager.common.NotFoundException;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

import java.util.LinkedHashMap;
import java.util.Map;

@Configuration
@EnableConfigurationProperties(IndexEventsProperties.class)
public class KafkaTopicsConfig {

    @Bean
    KafkaAdmin.NewTopics indexEventTopics(IndexEventsProperties properties) {
        return new KafkaAdmin.NewTopics(
                TopicBuilder.name(properties.topic()).partitions(1).replicas(1).build(),
                TopicBuilder.name(properties.deadLetterTopic()).partitions(1).replicas(1).build());
    }

    @Bean
    DefaultErrorHandler indexVersionErrorHandler(KafkaTemplate<Object, Object> kafkaTemplate,
                                                  ProducerFactory<Object, byte[]> producerFactory) {
        // При deserialization failure значением записи становятся исходные byte[]; JsonSerializer
        // повторно сериализовал бы их вместо сохранения оригинала в DLT, поэтому нужен отдельный
        // KafkaTemplate с ByteArraySerializer на том же (Boot'овском, wildcard-typed) ProducerFactory.
        // Не регистрируем его как отдельный @Bean: Boot откатывает свой автоконфигурированный
        // KafkaTemplate<Object,Object> по @ConditionalOnMissingBean(KafkaTemplate.class), которое
        // матчится по сырому типу и не смотрит на generics.
        // https://docs.spring.io/spring-kafka/reference/kafka/sending-messages.html#kafkatemplate
        KafkaTemplate<Object, byte[]> dltRawBytesTemplate = new KafkaTemplate<>(producerFactory,
                Map.of(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class));

        // LinkedHashMap гарантирует, что byte[] проверяется раньше общего Object.class.
        // https://docs.spring.io/spring-kafka/reference/kafka/annotation-error-handling.html#dead-letters
        Map<Class<?>, KafkaOperations<?, ?>> dltTemplates = new LinkedHashMap<>();
        dltTemplates.put(byte[].class, dltRawBytesTemplate);
        dltTemplates.put(Object.class, kafkaTemplate);

        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                dltTemplates,
                (record, ex) -> new TopicPartition(record.topic() + ".DLT", record.partition()));

        ExponentialBackOff backOff = new ExponentialBackOff();
        backOff.setInitialInterval(1_000L);
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(10_000L);
        backOff.setMaxAttempts(3);

        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        handler.addNotRetryableExceptions(NotFoundException.class);
        return handler;
    }
}
