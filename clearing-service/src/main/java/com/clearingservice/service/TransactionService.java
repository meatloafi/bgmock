package com.clearingservice.service;

import com.clearingservice.event.IncomingTransactionEvent;
import com.clearingservice.event.OutgoingTransactionEvent;
import com.clearingservice.event.TransactionResponseEvent;
import com.clearingservice.model.BankMapping;
import com.clearingservice.model.OutgoingTransaction;
import com.clearingservice.model.TransactionStatus;
import com.clearingservice.repository.BankMappingRepository;
import com.clearingservice.repository.IncomingTransactionRepository;
import com.clearingservice.repository.OutgoingTransactionRepository;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

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
            @Autowired(required = false) KafkaTemplate<String, IncomingTransactionEvent> forwardedTemplate,
            @Autowired(required = false) KafkaTemplate<String, TransactionResponseEvent> completedTemplate) {
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

        // Check if transaction already exists
        Optional<OutgoingTransaction> existing = outgoingRepo.findById(event.getTransactionId());
        if (existing.isPresent()) {
            log.info("Transaction {} already exists, skipping save", event.getTransactionId());
            return ResponseEntity.ok(existing.get());
        }

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
                event.getUpdatedAt());
        outgoingRepo.save(outgoing);
        log.info("Successfully saved transaction into DB: {}", outgoing);

        // 2. Kontrollera bankgiro → clearing + account
        Optional<BankMapping> mappingOpt = mappingRepo.findByBankgoodNumber(event.getToBankgoodNumber());

        if (mappingOpt.isEmpty()) {

            TransactionResponseEvent failedResponse = new TransactionResponseEvent();
            failedResponse.setTransactionId(event.getTransactionId());
            failedResponse.setStatus(TransactionStatus.FAILED);
            failedResponse.setMessage("No bank-mapping found for " + event.getToBankgoodNumber());

            completedTemplate.send(TOPIC_COMPLETED, event.getFromClearingNumber(), failedResponse);
            return ResponseEntity.ok("No bank-mapping found for " + event.getToBankgoodNumber());
        }
        // TODO, fixa rätt bankmapping för lookup
        /*
         * 
         * // Skicka FAILED response tillbaka till Bank A

         * 
         * // Key = ursprungsbanken (frånClearingNumber)
         * completedTemplate.send(TOPIC_COMPLETED, event.getFromClearingNumber(),
         * failedResponse);
         * ResponseEntity.badRequest().body("No bank mapping found for bankgiro");;
         * }
         * 
         * BankMapping mapping = mappingOpt.get();
         * 
         * // 3. Skapa IncomingTransactionEvent till mottagarbanken
         * IncomingTransactionEvent incomingEvent = new IncomingTransactionEvent(
         * event.getTransactionId(),
         * mapping.getClearingNumber(),
         * mapping.getAccountNumber(),
         * event.getAmount(),
         * TransactionStatus.PENDING,
         * event.getCreatedAt(),
         * LocalDateTime.now()
         * );
         * 
         * // 4. Skicka vidare → transactions.forwarded (med key = mottagarbankens
         * clearingnummer)
         * forwardedTemplate.send(TOPIC_FORWARDED, mapping.getClearingNumber(),
         * incomingEvent);
         * 
         * log.info("Forwarded incoming transaction to bank {} for account {}",
         * mapping.getClearingNumber(), mapping.getAccountNumber());
         * 
         */ // TODO, ta bort allt som är kommenterat när bankMapping är klar.

        // if (forwardedTemplate != null) {
        //     forwardedTemplate.send(TOPIC_FORWARDED,
        //             "00001",
        //             new IncomingTransactionEvent(
        //                     event.getTransactionId(),
        //                     "00001",
        //                     "1",
        //                     event.getAmount(),
        //                     TransactionStatus.PENDING,
        //                     event.getCreatedAt(),
        //                     LocalDateTime.now()));
        // }
        
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
            if (completedTemplate != null) {
                completedTemplate.send(
                        TOPIC_COMPLETED,
                        outgoing.getFromClearingNumber(),
                        event);

                log.info("Forwarded final response back to bank {}", outgoing.getFromClearingNumber());
            }
        });

        return ResponseEntity.ok("Processed transaction response forwarded successfully");
    }

    public ResponseEntity<?> getOutgoingTransactionById(UUID transactionId) {
        return outgoingRepo.findByTransactionId(transactionId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
