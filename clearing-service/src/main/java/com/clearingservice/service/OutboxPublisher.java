package com.clearingservice.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.clearingservice.model.OutboxEvent;
import com.clearingservice.repository.OutboxEventRepository;

import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class OutboxPublisher {
    
    @Autowired
    private OutboxEventRepository outboxEventRepository;
    
    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;
    
    @Scheduled(fixedRate = 1000) // Run every 1 second
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> pendingEvents = outboxEventRepository.findByPublishedFalseOrderByCreatedAtAsc();
        
        for (OutboxEvent event : pendingEvents) {
            try {
                // Send to Kafka and wait for acknowledgment
                kafkaTemplate.send(event.getTopic(), event.getMessageKey(), event.getPayload()).get();
                
                // Mark as published ONLY after Kafka acknowledges
                event.setPublished(true);
                outboxEventRepository.save(event);
                
                log.info("Published outbox event id={} for transaction={}", event.getId(), event.getTransactionId());
            } catch (Exception e) {
                log.error("Failed to publish outbox event id={}", event.getId(), e);
                // Don't mark as published - will retry on next poll
            }
        }
    }
}