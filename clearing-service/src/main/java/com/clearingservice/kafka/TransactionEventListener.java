package com.clearingservice.kafka;

import com.clearingservice.event.TransactionEvent;
import com.clearingservice.event.TransactionResponseEvent;
import com.clearingservice.service.TransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka-listener som tar emot TransactionEvent från Bank A
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionEventListener {

    private final TransactionService transactionService;

    @KafkaListener(
            topics = "transactions.outgoing", // Event från Bank A
            groupId = "clearing-service",
            containerFactory = "transactionListenerFactory"
    )
    public void listenTransactionEvent(TransactionEvent event) {
        log.info("📥 Mottog TransactionEvent från Bank A: {}", event.getTransactionId());

        try {
            transactionService.processIncomingTransaction(event);
        } catch (Exception e) {
            log.error("❌ Fel vid hantering av TransactionEvent {}: {}", event.getTransactionId(), e.getMessage(), e);

            // Skicka fail-response om något går fel
            transactionService.sendTransactionResponse(
                    new TransactionResponseEvent(
                            event.getTransactionId(),
                            com.clearingservice.model.TransactionStatus.FAILED,
                            "Internal error in clearing-service"
                    )
            );
        }
    }
}
