package com.clearingservice.kafka;

import com.bankgood.common.event.TransactionResponseEvent;
import com.bankgood.common.model.TransactionStatus;
import com.clearingservice.service.TransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka listener that receives TransactionResponseEvent from Bank B
 * and sends it back to Bank A.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionResponseListener {

    private final TransactionService transactionService;

    @KafkaListener(
            topics = "transactions.response", // Response from Bank B
            groupId = "clearing-service",
            containerFactory = "responseListenerFactory"
    )
    public void listenTransactionResponse(TransactionResponseEvent response) {
        log.info("📥 Received TransactionResponseEvent from Bank B: {}", response.getTransactionId());

        try {
            transactionService.handleBankResponse(response);
            log.info("✅ TransactionResponseEvent {} sent back to Bank A with status: {}",
                    response.getTransactionId(), response.getStatus());
        } catch (Exception e) {
            log.error("❌ Error handling TransactionResponseEvent {}: {}", response.getTransactionId(), e.getMessage(), e);

            // Send failure response if DB update fails
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
