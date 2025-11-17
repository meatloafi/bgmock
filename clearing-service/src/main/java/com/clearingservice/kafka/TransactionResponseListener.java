package com.clearingservice.kafka;

import com.clearingservice.event.TransactionResponseEvent;
import com.clearingservice.model.TransactionStatus;
import com.clearingservice.service.TransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka-listener som tar emot TransactionResponseEvent från Bank B
 * och skickar tillbaka till Bank A.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionResponseListener {

    private final TransactionService transactionService;

    @KafkaListener(
            topics = "transactions.response", // Response från Bank B
            groupId = "clearing-service",
            containerFactory = "responseListenerFactory"
    )
    public void listenTransactionResponse(TransactionResponseEvent response) {
        log.info("📥 Mottog TransactionResponseEvent från Bank B: {}", response.getTransactionId());

        try {
            transactionService.handleBankResponse(response);
            log.info("✅ TransactionResponseEvent {} skickad tillbaka till Bank A med status {}",
                    response.getTransactionId(), response.getStatus());
        } catch (Exception e) {
            log.error("❌ Fel vid hantering av TransactionResponseEvent {}: {}", response.getTransactionId(), e.getMessage(), e);

            // Skicka fail-response om DB-uppdatering misslyckas
            transactionService.sendTransactionResponse(
                    new TransactionResponseEvent(
                            response.getTransactionId(),
                            TransactionStatus.FAILED,
                            "Internal error in clearing-service"
                    )
            );
        }
    }
}
