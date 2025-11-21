package com.clearingservice.service;

import com.bankgood.common.event.IncomingTransactionEvent;
import com.bankgood.common.event.OutgoingTransactionEvent;
import com.bankgood.common.event.TransactionResponseEvent;
import com.clearingservice.model.BankMapping;
import com.clearingservice.model.OutgoingTransaction;
import com.bankgood.common.model.TransactionStatus;
import com.clearingservice.repository.BankMappingRepository;
import com.clearingservice.repository.IncomingTransactionRepository;
import com.clearingservice.repository.OutgoingTransactionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
public class TransactionService {

    private static final String TOPIC_FORWARDED = "transactions.forwarded";
    private static final String TOPIC_COMPLETED = "transactions.completed";

    private final OutgoingTransactionRepository outgoingRepo;
    private final IncomingTransactionRepository incomingRepo;
    private final BankMappingRepository mappingRepo;

    private final KafkaTemplate<String, IncomingTransactionEvent> forwardedTemplate;
    private final KafkaTemplate<String, TransactionResponseEvent> completedTemplate;

    public TransactionService(
            OutgoingTransactionRepository outgoingRepo,
            IncomingTransactionRepository incomingRepo,
            BankMappingRepository mappingRepo,
            KafkaTemplate<String, IncomingTransactionEvent> forwardedTemplate,
            KafkaTemplate<String, TransactionResponseEvent> completedTemplate
    ) {
        this.outgoingRepo = outgoingRepo;
        this.incomingRepo = incomingRepo;
        this.mappingRepo = mappingRepo;
        this.forwardedTemplate = forwardedTemplate;
        this.completedTemplate = completedTemplate;
    }

    /**
     * CONSUMER: transactions.initiated
     * Bank A skickar TransactionOutgoingEvent → Clearing-service
     */
    @Transactional
    public ResponseEntity<?> handleOutgoingTransaction(OutgoingTransactionEvent event) {
        log.info("Clearing-service received OutgoingTransactionEvent for bankgiro {}", event.getToBankgoodNumber());

        // 1. Spara outgoing transaction internt hos clearing
        OutgoingTransaction outgoing = new OutgoingTransaction(
                event.getTransactionId(),
                event.getFromAccountId(),
                event.getFromClearingNumber(),
                event.getFromAccountNumber(),
                event.getToBankgoodNumber(),
                event.getAmount(),
                event.getStatus(),
                event.getCreatedAt(),
                event.getUpdatedAt()
        );
        outgoingRepo.save(outgoing);

        // 2. Look up bank mapping based on bankgiro number
        Optional<BankMapping> mappingOpt = mappingRepo.findByBankgoodNumber(event.getToBankgoodNumber());

        if (mappingOpt.isEmpty()) {
            log.warn("No mapping found for bankgiro {}", event.getToBankgoodNumber());

            // Send FAILED response back to Bank A
            TransactionResponseEvent failedResponse = new TransactionResponseEvent(
                    event.getTransactionId(),
                    TransactionStatus.FAILED,
                    "No bank mapping found for bankgiro: " + event.getToBankgoodNumber()
            );

            // Key = sender bank (fromClearingNumber)
            completedTemplate.send(TOPIC_COMPLETED, event.getFromClearingNumber(), failedResponse);
            return ResponseEntity.badRequest().body("No bank mapping found for bankgiro: " + event.getToBankgoodNumber());
        }

        BankMapping mapping = mappingOpt.get();

        // 3. Create IncomingTransactionEvent for recipient bank
        IncomingTransactionEvent incomingEvent = new IncomingTransactionEvent(
                event.getTransactionId(),
                mapping.getClearingNumber(),
                mapping.getAccountNumber(),
                event.getAmount(),
                TransactionStatus.PENDING,
                event.getCreatedAt(),
                LocalDateTime.now()
        );

        // 4. Forward to recipient bank via transactions.forwarded topic
        // Key = recipient bank clearing number (ensures ordering per bank)
        forwardedTemplate.send(TOPIC_FORWARDED, mapping.getClearingNumber(), incomingEvent);

        log.info("Forwarded incoming transaction to bank {} for account {}",
                mapping.getClearingNumber(), mapping.getAccountNumber());


        return ResponseEntity.ok("Outgoing transaction processed and forwarded");
    }


    /**
     * CONSUMER: transactions.processed
     * Bank B skickar response → Clearing-service
     */
    @Transactional
    public ResponseEntity<?> handleProcessedTransaction(TransactionResponseEvent event) {

        log.info("Clearing-service received TransactionResponseEvent for {}", event.getTransactionId());

        // 1. Uppdatera incoming-transaction internt
        incomingRepo.findByTransactionId(event.getTransactionId()).ifPresent(incoming -> {
            incoming.setStatus(event.getStatus());
            incomingRepo.save(incoming);
        });

        // 2. Hämta outgoing transaktionen för att veta vilken bank som ska få svaret
        outgoingRepo.findByTransactionId(event.getTransactionId()).ifPresent(outgoing -> {

            // Skicka tillbaka → transactions.completed (med key = avsändarbankens clearingnummer)
            completedTemplate.send(
                    TOPIC_COMPLETED,
                    outgoing.getFromClearingNumber(),
                    event
            );

            log.info("Forwarded final response back to bank {}", outgoing.getFromClearingNumber());
        });

        return ResponseEntity.ok("Processed transaction response forwarded successfully");
    }
}
