package com.bankgood.bank.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "kafka.enabled", havingValue = "true", matchIfMissing = true)
public class PaymentConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentConsumer.class);

    @KafkaListener(topics = "payment.prepare", groupId = "${spring.kafka.consumer.group-id}")
    public void consume(String message) {
        LOGGER.info(String.format("Consumed message from 'payment.prepare' -> %s", message));
        // Logic for handling the "prepare" phase would go here
    }
}
