package com.clearingservice.kafka;

import com.clearingservice.event.TransactionEvent;
import com.clearingservice.event.TransactionResponseEvent;
import com.clearingservice.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TransactionListener {

    private final TransactionService service;

    // Lyssnar på inkommande transaktioner från banker
    @KafkaListener(topics = "transactions.outgoing", groupId = "clearing-service")
    public void listenOutgoing(TransactionEvent transactionEvent) {
        service.processIncomingTransaction(transactionEvent);
    }

    // Lyssnar på svar från mottagarbanker
    @KafkaListener(topics = "transactions.response", groupId = "clearing-service")
    public void listenBankResponses(TransactionResponseEvent response) {
        service.handleBankResponse(response);
    }
}
