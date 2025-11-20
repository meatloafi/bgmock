package com.bankgood.bank.kafka;

import com.bankgood.common.event.IncomingTransactionEvent;
import com.bankgood.common.event.TransactionEvent;
import com.bankgood.common.event.TransactionResponseEvent;
import com.bankgood.bank.service.TransactionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class TransactionEventListener {

    private final TransactionService transactionService;

    public TransactionEventListener(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    // (0) Listen to outgoing events from clearing-service
    @KafkaListener(
            topics = "transactions.outgoing",
            groupId = "bank-service",
            containerFactory = "transactionListenerFactory"
    )
    public void listenTransactionEvent(IncomingTransactionEvent event) {
        log.info("📥 Received TransactionEvent from clearing-service: {}", event.getTransactionId());

        try {
            transactionService.handleIncomingTransaction(event);
            log.info("✅ TransactionEvent {} processed and response sent", event.getTransactionId());
        } catch (Exception e) {
            log.error("❌ Error handling TransactionEvent {}: {}", event.getTransactionId(), e.getMessage(), e);
        }
    }

    // (1) Listen to transactions forwarded to this bank
    @KafkaListener(topics = "transactions.forwarded", groupId = "bank-group")
    public void listenIncoming(IncomingTransactionEvent event) {
        transactionService.handleIncomingTransaction(event);
    }

    // (2) Listen to completed transactions (responses)
    @KafkaListener(topics = "transactions.completed", groupId = "bank-group")
    public void listenCompleted(TransactionResponseEvent event) {
        transactionService.handleCompletedTransaction(event);
    }
}
