package com.bankgood.bank.kafka;

import com.bankgood.common.event.TransactionEvent;
import com.bankgood.bank.service.TransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "kafka.enabled", havingValue = "true", matchIfMissing = true)
public class TransactionEventListener {

    private final TransactionService transactionService;

    @KafkaListener(
            topics = "transactions.outgoing", // Event from clearing-service
            groupId = "bank-service",
            containerFactory = "transactionListenerFactory"
    )
    public void listenTransactionEvent(TransactionEvent event) {
        log.info("📥 Received TransactionEvent from clearing-service: {}", event.getTransactionId());

        try {
            // Bank B handles incoming transaction
            transactionService.handleIncomingTransaction(event);
            log.info("✅ TransactionEvent {} processed and response sent", event.getTransactionId());
        } catch (Exception e) {
            log.error("❌ Error handling TransactionEvent {}: {}", event.getTransactionId(), e.getMessage(), e);
        }
    }
}
