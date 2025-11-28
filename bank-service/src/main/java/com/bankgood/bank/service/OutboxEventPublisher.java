package com.bankgood.bank.service;

import java.util.List;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.bankgood.bank.model.OutboxEvent;
import com.bankgood.bank.repository.OutboxEventRepository;

import lombok.extern.slf4j.Slf4j;
import com.bankgood.bank.event.OutgoingTransactionEvent;
import com.bankgood.bank.event.TransactionResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
@Slf4j
public class OutboxEventPublisher {

    private final OutboxEventRepository outboxEventRepo;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public OutboxEventPublisher(OutboxEventRepository outboxEventRepo,
                                KafkaTemplate<String, Object> kafkaTemplate,
                                ObjectMapper objectMapper) {
        this.outboxEventRepo = outboxEventRepo;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedRate = 1000)
    public void publishPendingEvents() {
        List<OutboxEvent> pendingEvents = outboxEventRepo.findByPublishedFalseOrderByCreatedAtAsc();

        for (OutboxEvent event : pendingEvents) {
            try {
                Object payloadObj;

                // Convert payload from string to correct event object
                switch (event.getTopic()) {
                    case "transactions.initiated":
                        payloadObj = objectMapper.readValue(event.getPayload(), OutgoingTransactionEvent.class);
                        break;
                    case "transactions.processed":
                        payloadObj = objectMapper.readValue(event.getPayload(), TransactionResponseEvent.class);
                        break;
                    default:
                        log.warn("Unknown topic {} for OutboxEvent id={}", event.getTopic(), event.getId());
                        continue; 
                }

                // Send to Kafka
                kafkaTemplate.send(event.getTopic(), event.getMessageKey(), payloadObj).get();
                log.info(payloadObj.toString());
                // Mark as published only after successful send
                event.setPublished(true);
                outboxEventRepo.save(event);

                log.info("Published outbox event with transaction ID: {} to clearing number: {}", event.getTransactionId(), event.getMessageKey());

            } catch (Exception e) {
                log.error("Failed to publish outbox event id={}", event.getId(), e);
            }
        }
    }
}
