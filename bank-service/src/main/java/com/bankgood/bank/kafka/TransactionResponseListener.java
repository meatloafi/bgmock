package com.bankgood.bank.kafka;

import com.bankgood.bank.event.TransactionResponseEvent;
import com.bankgood.bank.service.TransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionResponseListener {

    private final TransactionService transactionService;

    @KafkaListener(topics = "transactions.response", groupId = "bank-service")
    public void listen(TransactionResponseEvent response) {
        log.info("📥 Mottog TransactionResponseEvent från Kafka: {}", response);

        try {
            transactionService.handleTransactionResponse(response);
            log.info("✅ Transaction {} uppdaterad via Kafka med status: {}",
                    response.getTransactionId(), response.getStatus());
        } catch (Exception e) {
            log.error("❌ Fel vid hantering av TransactionResponseEvent {}: {}",
                    response.getTransactionId(), e.getMessage(), e);
        }
    }
}
