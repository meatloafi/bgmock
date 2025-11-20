package com.bankgood.bank.kafka;

import com.bankgood.common.event.IncomingTransactionEvent;
import com.bankgood.common.event.TransactionResponseEvent;
import com.bankgood.bank.service.TransactionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class TransactionEventListener {

    private final TransactionService transactionService;

    public TransactionEventListener(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    // (1) Bank tar emot transaktion från clearing → ska behandla den
    @KafkaListener(topics = "transactions.forwarded", groupId = "${spring.kafka.consumer.group-id:bank-group}")
    public void listenIncoming(IncomingTransactionEvent event) {
        transactionService.handleIncomingTransaction(event);
    }

    // (2) Bank tar emot respons för en outgoing transaktion
    @KafkaListener(topics = "transactions.completed", groupId = "${spring.kafka.consumer.group-id:bank-group}")
    public void listenCompleted(TransactionResponseEvent event) {
        transactionService.handleCompletedTransaction(event);
    }
}
