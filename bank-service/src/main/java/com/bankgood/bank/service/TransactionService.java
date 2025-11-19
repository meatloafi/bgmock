package com.bankgood.bank.service;

import com.bankgood.bank.event.IncomingTransactionEvent;
import com.bankgood.bank.event.OutgoingTransactionEvent;
import com.bankgood.bank.event.TransactionResponseEvent;
import com.bankgood.bank.model.Account;
import com.bankgood.bank.model.IncomingTransaction;
import com.bankgood.bank.model.OutgoingTransaction;
import com.bankgood.bank.model.TransactionStatus;
import com.bankgood.bank.repository.AccountRepository;
import com.bankgood.bank.repository.IncomingTransactionRepository;
import com.bankgood.bank.repository.OutgoingTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
public class TransactionService {

    @Value("${BANK_CLEARING_NUMBER}")
    private String fromClearingNumber;

    private final OutgoingTransactionRepository outgoingRepo;
    private final IncomingTransactionRepository incomingRepo;
    private final AccountRepository accountRepo;

    public TransactionService(OutgoingTransactionRepository outgoingRepo,
                              IncomingTransactionRepository incomingRepo,
                              AccountRepository accountRepo, KafkaTemplate<String, OutgoingTransactionEvent> outgoingTransactionKafkaTemplate, KafkaTemplate<String, IncomingTransactionEvent> incomingTransactionKafkaTemplate, KafkaTemplate<String, TransactionResponseEvent> responseKafkaTemplate) {
        this.outgoingRepo = outgoingRepo;
        this.incomingRepo = incomingRepo;
        this.accountRepo = accountRepo;
        this.outgoingTransactionKafkaTemplate = outgoingTransactionKafkaTemplate;
        this.incomingTransactionKafkaTemplate = incomingTransactionKafkaTemplate;
        this.responseKafkaTemplate = responseKafkaTemplate;
    }

    private final KafkaTemplate<String, OutgoingTransactionEvent> outgoingTransactionKafkaTemplate;
    private final KafkaTemplate<String, IncomingTransactionEvent> incomingTransactionKafkaTemplate;
    private final KafkaTemplate<String, TransactionResponseEvent> responseKafkaTemplate;

    private static final String TOPIC_INITIATED = "transactions.initiated";
    private static final String TOPIC_FORWARDED = "transactions.forwarded";
    private static final String TOPIC_PROCESSED = "transactions.processed";
    private static final String TOPIC_COMPLETED = "transactions.completed";

    // ===================== OUTGOING =====================
    public OutgoingTransaction createOutgoingTransaction(OutgoingTransactionEvent event) {
        OutgoingTransaction transaction = new OutgoingTransaction(
                event.getFromAccountId(),
                event.getFromClearingNumber(),
                event.getFromAccountNumber(),
                event.getToBankgoodNumber(),
                event.getAmount()
        );
        transaction.setStatus(event.getStatus());
        return outgoingRepo.save(transaction);
    }

    public Optional<OutgoingTransaction> getOutgoingTransaction(UUID id) {
        return outgoingRepo.findById(id);
    }

    public List<OutgoingTransaction> getAllOutgoingTransactions() {
        return outgoingRepo.findAll();
    }

    public OutgoingTransaction updateOutgoingTransaction(UUID id, OutgoingTransactionEvent event) {
        Optional<OutgoingTransaction> opt = outgoingRepo.findById(id);
        if (opt.isPresent()) {
            OutgoingTransaction transaction = opt.get();
            transaction.setFromAccountId(event.getFromAccountId());
            transaction.setFromClearingNumber(event.getFromClearingNumber());
            transaction.setFromAccountNumber(event.getFromAccountNumber());
            transaction.setToBankgoodNumber(event.getToBankgoodNumber());
            transaction.setAmount(event.getAmount());
            transaction.setStatus(event.getStatus());
            return outgoingRepo.save(transaction);
        }
        return null;
    }

    public void deleteOutgoingTransaction(UUID id) {
        outgoingRepo.deleteById(id);
    }

    // ===================== INCOMING =====================
    public IncomingTransaction createIncomingTransaction(IncomingTransactionEvent event) {
        IncomingTransaction transaction = new IncomingTransaction(
                event.getToClearingNumber(),
                event.getToAccountNumber(),
                event.getAmount()
        );
        transaction.setStatus(event.getStatus());
        return incomingRepo.save(transaction);
    }

    public Optional<IncomingTransaction> getIncomingTransaction(UUID id) {
        return incomingRepo.findById(id);
    }

    public List<IncomingTransaction> getAllIncomingTransactions() {
        return incomingRepo.findAll();
    }

    public IncomingTransaction updateIncomingTransaction(UUID id, IncomingTransactionEvent event) {
        Optional<IncomingTransaction> opt = incomingRepo.findById(id);
        if (opt.isPresent()) {
            IncomingTransaction transaction = opt.get();
            transaction.setToClearingNumber(event.getToClearingNumber());
            transaction.setToAccountNumber(event.getToAccountNumber());
            transaction.setAmount(event.getAmount());
            transaction.setStatus(event.getStatus());
            return incomingRepo.save(transaction);
        }
        return null;
    }

    public void deleteIncomingTransaction(UUID id) {
        incomingRepo.deleteById(id);
    }
}
