package com.clearingservice.kafka;

import com.bankgood.common.event.OutgoingTransactionEvent;
import com.bankgood.common.event.TransactionResponseEvent;
import com.clearingservice.service.TransactionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class TransactionEventListener {

    private final TransactionService transactionService;

    public TransactionEventListener(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    // Konsumerar outgoing-transaktioner initierade av bank-service
    @KafkaListener(topics = "transactions.initiated", groupId = "${spring.kafka.consumer.group-id:clearing-service}")
    public void listenOutgoing(OutgoingTransactionEvent event) {
        transactionService.handleOutgoingTransaction(event);
    }

    // Konsumerar processed-transaktioner från bank-service (response)
    @KafkaListener(topics = "transactions.processed", groupId = "${spring.kafka.consumer.group-id:clearing-service}")
    public void listenProcessed(TransactionResponseEvent event) {
        transactionService.handleProcessedTransaction(event);
    }
}
