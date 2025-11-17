package com.bankgood.bank.controller;

import com.bankgood.bank.kafka.PaymentProducer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/kafka")
@ConditionalOnProperty(name = "kafka.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaController {

    private final PaymentProducer paymentProducer;

    public KafkaController(PaymentProducer paymentProducer) {
        this.paymentProducer = paymentProducer;
    }

    @PostMapping("/publish")
    public ResponseEntity<String> publish(@RequestBody String message) {
        paymentProducer.sendMessage(message);
        return ResponseEntity.ok("Message sent to Kafka topic 'payment.requests'");
    }
}
