package com.clearingservice.kafka;

import com.clearingservice.event.OutgoingTransactionEvent;
import com.clearingservice.event.TransactionResponseEvent;
import com.clearingservice.service.TransactionService;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "kafka.enabled", havingValue = "true", matchIfMissing = true)
public class TransactionEventListener {

    private final TransactionService transactionService;

    public TransactionEventListener(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    // Konsumerar outgoing-transaktioner initierade av bank-service
    @KafkaListener(
            topics = "transactions.initiated",
            groupId = "clearing-service-initiated",
            containerFactory = "outgoingListenerFactory"
    )
    public void listenOutgoing(OutgoingTransactionEvent event) {
        transactionService.handleOutgoingTransaction(event);
    }

    // Konsumerar processed-transaktioner från bank-service (response)
    @KafkaListener(
            topics = "transactions.processed",
            groupId = "clearing-service-processed",
            containerFactory = "responseListenerFactory"
    )
    public void listenProcessed(TransactionResponseEvent event) {
        transactionService.handleProcessedTransaction(event);
    }
}