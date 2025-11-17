package com.bankgood.bank.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "kafka.enabled", havingValue = "true", matchIfMissing = true)
public class PaymentProducer {

    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentProducer.class);
    private static final String TOPIC = "payment.requests";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public PaymentProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendMessage(String message) {
        LOGGER.info(String.format("Producing message -> %s", message));
        this.kafkaTemplate.send(TOPIC, message);
    }
}
