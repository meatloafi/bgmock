package com.clearingservice.service;

import com.clearingservice.event.TransactionEvent;
import com.clearingservice.event.TransactionResponseEvent;
import com.clearingservice.model.ClearingTransaction;
import com.clearingservice.model.TransactionStatus;
import com.clearingservice.repository.ClearingTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ClearingTransactionService {

    private final ClearingTransactionRepository repository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    // Hårdkodat exempel, kan bytas till properties/config
    private final Map<String, String> bankRegistry = new HashMap<>() {{
        put("000001", "transactions.incoming.BankA");
        put("000002", "transactions.incoming.BankB");
    }};

    private final String RESPONSE_TOPIC = "transactions.response";

    @Transactional
    public void processIncomingTransaction(TransactionEvent dto) {
        //TODO, Gör en slags lookup för att översätta bankgood number till clearing och account number.
        // Spara PENDING i DB
        ClearingTransaction tx = new ClearingTransaction();
        tx.setTransactionId(dto.getTransactionId());
        tx.setFromAccountNumber(dto.getFromAccountNumber());
        tx.setFromClearingNumber(dto.getFromClearingNumber());
        tx.setToClearingNumber(dto.getToClearingNumber());
        tx.setToAccountNumber(dto.getToAccountNumber());
        tx.setAmount(dto.getAmount());
        tx.setStatus(TransactionStatus.PENDING);
        tx.setCreatedAt(LocalDateTime.now());
        tx.setUpdatedAt(LocalDateTime.now());

        repository.save(tx);

        // Hitta mottagarbank-topic
        String bankTopic = bankRegistry.get(dto.getToClearingNumber());

        if (bankTopic == null) {
            // Ingen bank hittad → FAIL
            tx.setStatus(TransactionStatus.FAILED);
            tx.setFailureReason("Recipient bank unknown");
            tx.setUpdatedAt(LocalDateTime.now());
            repository.save(tx);

            TransactionResponseEvent response = new TransactionResponseEvent(
                    dto.getTransactionId(),
                    TransactionStatus.FAILED,
                    "Recipient bank unknown"
            );
            kafkaTemplate.send(RESPONSE_TOPIC, response);
            return;
        }

        // Forward till mottagarbank
        kafkaTemplate.send(bankTopic, dto);
    }

    @Transactional
    public void handleBankResponse(TransactionResponseEvent response) {
        ClearingTransaction tx = repository.findByTransactionId(response.getTransactionId())
                .orElseThrow(() -> new RuntimeException("Transaction not found"));

        tx.setStatus("SUCCESS".equals(response.getStatus()) ? TransactionStatus.SUCCESS : TransactionStatus.FAILED);
        tx.setFailureReason(response.getMessage());
        tx.setUpdatedAt(LocalDateTime.now());

        repository.save(tx);

        // Skicka tillbaka till avsändande bank
        kafkaTemplate.send(RESPONSE_TOPIC, response);
    }
}
