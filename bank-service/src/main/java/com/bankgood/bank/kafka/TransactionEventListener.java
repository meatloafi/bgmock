package com.bankgood.bank.kafka;

import com.bankgood.bank.event.TransactionEvent;
import com.bankgood.bank.service.TransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionEventListener {

    private final TransactionService transactionService;

    @KafkaListener(
            topics = "transactions.outgoing", // Event från clearing-service
            groupId = "bank-service",
            containerFactory = "transactionListenerFactory"
    )
    public void listenTransactionEvent(TransactionEvent event) {
        log.info("📥 Mottog TransactionEvent från clearing-service: {}", event.getTransactionId());

        try {
            // Bank B hanterar inkommande transaktion
            transactionService.handleIncomingTransaction(event);
            log.info("✅ TransactionEvent {} behandlad och svar skickat", event.getTransactionId());
        } catch (Exception e) {
            log.error("❌ Fel vid hantering av TransactionEvent {}: {}", event.getTransactionId(), e.getMessage(), e);
        }
    }
}
