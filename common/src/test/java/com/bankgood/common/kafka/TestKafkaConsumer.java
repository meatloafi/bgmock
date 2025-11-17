package com.bankgood.common.kafka;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import java.util.Collections;
import java.util.Map;

public class TestKafkaConsumer<T> {

    private final Consumer<String, T> consumer;

    public TestKafkaConsumer(EmbeddedKafkaBroker embeddedKafkaBroker, String topic, Class<T> valueType) {
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps("test-group", "true", embeddedKafkaBroker);
        JsonDeserializer<T> jsonDeserializer = new JsonDeserializer<>(valueType);
        jsonDeserializer.addTrustedPackages("*");
        this.consumer = new DefaultKafkaConsumerFactory<>(consumerProps, new StringDeserializer(), jsonDeserializer).createConsumer();
        this.consumer.subscribe(Collections.singletonList(topic));
    }

    public Consumer<String, T> getConsumer() {
        return consumer;
    }

    public void close() {
        this.consumer.close();
    }
}
