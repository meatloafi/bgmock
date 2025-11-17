package com.clearingservice.config;

import com.bankgood.common.event.TransactionEvent;
import com.bankgood.common.event.TransactionResponseEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.KafkaTemplate;

@Configuration
@EnableKafka
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Bean
    public KafkaTemplate<String, TransactionEvent> transactionKafkaTemplate() {
        return new KafkaTemplate<>((org.springframework.kafka.core.ProducerFactory<String, TransactionEvent>) (Object) com.bankgood.common.config.KafkaConfig.producerFactory(bootstrapServers));
    }

    @Bean
    public KafkaTemplate<String, TransactionResponseEvent> responseKafkaTemplate() {
        return new KafkaTemplate<>((org.springframework.kafka.core.ProducerFactory<String, TransactionResponseEvent>) (Object) com.bankgood.common.config.KafkaConfig.producerFactory(bootstrapServers));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, TransactionEvent> transactionListenerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, TransactionEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(com.bankgood.common.config.KafkaConfig.consumerFactory(bootstrapServers, TransactionEvent.class, "clearing-service"));
        return factory;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, TransactionResponseEvent> responseListenerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, TransactionResponseEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(com.bankgood.common.config.KafkaConfig.consumerFactory(bootstrapServers, TransactionResponseEvent.class, "clearing-service"));
        return factory;
    }
}
