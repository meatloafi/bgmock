package com.clearingservice.config;

import com.clearingservice.event.TransactionEvent;
import com.clearingservice.event.TransactionResponseEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka-konfiguration för clearing-service.
 *
 * Clearing-service fungerar som bankgirot:
 * - Tar emot TransactionEvent från banker (Bank A) och översätter bankgiro → clearing+konto
 * - Skickar vidare TransactionEvent till mottagarbanken (Bank B)
 * - Tar emot TransactionResponseEvent från Bank B
 * - Skickar tillbaka TransactionResponseEvent till Bank A
 *
 * Därför behöver clearing-service både producera och konsumera båda typerna av events.
 */
@Configuration
@EnableKafka
public class KafkaConfig {

    // Kafka-brokern deployas i clearing-service
    private final String bootstrapServers = "localhost:9092"; // TODO Ändra till Kubernetes-deployment url

    // ------------------- Producer för TransactionEvent -------------------
    @Bean
    public ProducerFactory<String, TransactionEvent> transactionProducerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    public KafkaTemplate<String, TransactionEvent> transactionKafkaTemplate() {
        return new KafkaTemplate<>(transactionProducerFactory());
    }

    // ------------------- Producer för TransactionResponseEvent -------------------
    @Bean
    public ProducerFactory<String, TransactionResponseEvent> responseProducerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    public KafkaTemplate<String, TransactionResponseEvent> responseKafkaTemplate() {
        return new KafkaTemplate<>(responseProducerFactory());
    }

    // ------------------- Consumer för TransactionEvent -------------------
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, TransactionEvent> transactionListenerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, TransactionEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory(TransactionEvent.class));
        return factory;
    }

    // ------------------- Consumer för TransactionResponseEvent -------------------
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, TransactionResponseEvent> responseListenerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, TransactionResponseEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory(TransactionResponseEvent.class));
        return factory;
    }

    /**
     * Generisk consumer-factory som kan användas för olika event-typer.
     *
     * @param clazz Den klass som konsumeras (TransactionEvent eller TransactionResponseEvent)
     * @param <T> Typen av event
     * @return ConsumerFactory
     */
    private <T> ConsumerFactory<String, T> consumerFactory(Class<T> clazz) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "clearing-service"); // Alla clearing-instanser konsumerar med samma group
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);

        // Säkerställer att JsonDeserializer kan deserialisera rätt paket/klass
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.clearingservice.event");
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, clazz.getName());

        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(),
                new JsonDeserializer<>(clazz, false));
    }
}
