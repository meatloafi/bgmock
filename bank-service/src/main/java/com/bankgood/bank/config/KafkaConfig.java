package com.bankgood.bank.config;

import com.bankgood.bank.event.IncomingTransactionEvent;
import com.bankgood.bank.event.OutgoingTransactionEvent;
import com.bankgood.bank.event.TransactionResponseEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

@EnableKafka
@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    // @Value("${spring.kafka.consumer.group-id}")
    // private String groupId;


    // ===================== PRODUCER =====================

    private <T> ProducerFactory<String, T> producerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    public KafkaTemplate<String, OutgoingTransactionEvent> outgoingTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    @Bean
    public KafkaTemplate<String, TransactionResponseEvent> responseTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }


    // ===================== CONSUMER =====================

    private <T> ConsumerFactory<String, T> consumerFactory(Class<T> eventClass) {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        // config.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        JsonDeserializer<T> jsonDeserializer = new JsonDeserializer<>(eventClass);
        jsonDeserializer.addTrustedPackages("*");
        jsonDeserializer.ignoreTypeHeaders(); // kan hjälpa med __TypeId__ problem
        jsonDeserializer.setRemoveTypeHeaders(false);

        return new DefaultKafkaConsumerFactory<>(
                config,
                new StringDeserializer(),
                jsonDeserializer
        );
    }


    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, IncomingTransactionEvent> incomingListenerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, IncomingTransactionEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory(IncomingTransactionEvent.class));
        return factory;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, TransactionResponseEvent> responseListenerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, TransactionResponseEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory(TransactionResponseEvent.class));
        return factory;
    }

}
