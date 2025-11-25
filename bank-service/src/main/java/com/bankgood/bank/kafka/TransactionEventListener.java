package com.bankgood.bank.kafka;

import com.bankgood.bank.event.IncomingTransactionEvent;
import com.bankgood.bank.event.TransactionResponseEvent;
import com.bankgood.bank.service.TransactionService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "kafka.enabled", havingValue = "true", matchIfMissing = true)
public class TransactionEventListener {

    // @Value("${spring.kafka.consumer.group-id}")
    // private String groupId;

    private final TransactionService transactionService;

    public TransactionEventListener(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    // (1) Bank tar emot transaktion från clearing → ska behandla den
    @KafkaListener(
            topics = "transactions.forwarded",
            groupId = "bank-service-forwarded",
            containerFactory = "incomingListenerFactory"
    )
    public void listenIncoming(IncomingTransactionEvent event) {
        transactionService.handleIncomingTransaction(event);
    }


    // (2) Bank tar emot respons för en outgoing transaktion
    @KafkaListener(
            topics = "transactions.completed",
            groupId = "bank-service-completed",
            containerFactory = "responseListenerFactory"
    )
    public void listenCompleted(TransactionResponseEvent event) {
        transactionService.handleCompletedTransaction(event);
    }
}
