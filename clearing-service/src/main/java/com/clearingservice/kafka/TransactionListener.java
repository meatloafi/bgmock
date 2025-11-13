package com.clearingservice.kafka;

import com.clearingservice.event.TransactionEvent;
import com.clearingservice.event.TransactionResponseEvent;
import com.clearingservice.service.ClearingTransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TransactionListener {

    private final ClearingTransactionService service;

    // Lyssnar på inkommande transaktioner från bankerna
    @KafkaListener(topics = "transactions.outgoing", groupId = "clearing-service")
    public void listenOutgoing(TransactionEvent dto) {
        service.processIncomingTransaction(dto);
    }

    // Lyssnar på svar från mottagarbanker
    @KafkaListener(topics = {"transactions.incoming.BankA", "transactions.incoming.BankB"}, groupId = "clearing-service")
    public void listenBankResponses(TransactionResponseEvent response) {
        service.handleBankResponse(response);
    }
}
