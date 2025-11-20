package com.clearingservice.kafka;

import com.clearingservice.event.OutgoingTransactionEvent;
import com.clearingservice.event.TransactionResponseEvent;
import com.clearingservice.service.TransactionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class TransactionEventListener {

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    private final TransactionService transactionService;

    public TransactionEventListener(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    // Konsumerar outgoing-transaktioner initierade av bank-service
    @KafkaListener(topics = "transactions.initiated", groupId = "#{__listener.groupId}")
    public void listenOutgoing(OutgoingTransactionEvent event) {
        transactionService.handleOutgoingTransaction(event);
    }

    // Konsumerar processed-transaktioner från bank-service (response)
    @KafkaListener(topics = "transactions.processed", groupId = "#{__listener.groupId}")
    public void listenProcessed(TransactionResponseEvent event) {
        transactionService.handleProcessedTransaction(event);
    }
}
