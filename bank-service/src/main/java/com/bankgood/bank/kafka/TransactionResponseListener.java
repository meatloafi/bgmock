package com.bankgood.bank.kafka;

import com.bankgood.common.event.TransactionResponseEvent;
import com.bankgood.bank.service.TransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "kafka.enabled", havingValue = "true", matchIfMissing = true)
public class TransactionResponseListener {

    private final TransactionService transactionService;

    @KafkaListener(
            topics = "transactions.response", // Response från clearing-service
            groupId = "bank-service",
            containerFactory = "responseListenerFactory"
    )
    public void listenTransactionResponse(TransactionResponseEvent response) {
        log.info("📥 Mottog TransactionResponseEvent från clearing-service: {}", response.getTransactionId());

        try {
            // Bank A hanterar inkommande response
            transactionService.handleTransactionResponse(response);
            log.info("✅ Transaction {} uppdaterad med status: {}", response.getTransactionId(), response.getStatus());
        } catch (Exception e) {
            log.error("❌ Fel vid hantering av TransactionResponseEvent {}: {}", response.getTransactionId(), e.getMessage(), e);
        }
    }
}
