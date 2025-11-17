package com.clearingservice.kafka;

import com.bankgood.common.event.TransactionEvent;
import com.bankgood.common.event.TransactionResponseEvent;
import com.bankgood.common.model.TransactionStatus;
import com.clearingservice.service.TransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka listener that receives TransactionEvent from Bank A
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionEventListener {

    private final TransactionService transactionService;

    @KafkaListener(
            topics = "transactions.outgoing", // Event from Bank A
            groupId = "clearing-service",
            containerFactory = "transactionListenerFactory"
    )
    public void listenTransactionEvent(TransactionEvent event) {
        log.info("📥 Received TransactionEvent from Bank A: {}", event.getTransactionId());

        try {
            transactionService.processIncomingTransaction(event);
        } catch (Exception e) {
            log.error("❌ Error handling TransactionEvent {}: {}", event.getTransactionId(), e.getMessage(), e);

            // Send failure response if something goes wrong
            transactionService.sendTransactionResponse(
                    new TransactionResponseEvent(
                            event.getTransactionId(),
                            TransactionStatus.FAILED,
                            "Internal error in clearing-service"
                    )
            );
        }
    }
}
