package com.clearingservice.service;

import com.clearingservice.event.IncomingTransactionEvent;
import com.clearingservice.event.OutgoingTransactionEvent;
import com.clearingservice.event.TransactionResponseEvent;
import com.clearingservice.model.BankMapping;
import com.clearingservice.model.OutboxEvent;
import com.clearingservice.model.OutgoingTransaction;
import com.clearingservice.model.TransactionStatus;
import com.clearingservice.repository.BankMappingRepository;
import com.clearingservice.repository.OutboxEventRepository;
import com.clearingservice.repository.OutgoingTransactionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TransactionService in clearing-service.
 */
@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private OutgoingTransactionRepository outgoingRepo;

    @Mock
    private BankMappingRepository mappingRepo;

    @Mock
    private OutboxEventRepository outboxEventRepo;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @InjectMocks
    private TransactionService transactionService;

    private OutgoingTransactionEvent testEvent;
    private BankMapping testMapping;
    private UUID testTransactionId;
    private UUID testAccountId;

    @BeforeEach
    void setUp() {
        testTransactionId = UUID.randomUUID();
        testAccountId = UUID.randomUUID();

        testEvent = new OutgoingTransactionEvent();
        testEvent.setTransactionId(testTransactionId);
        testEvent.setFromAccountId(testAccountId);
        testEvent.setFromClearingNumber("000001");
        testEvent.setFromAccountNumber("ACC123456789");
        testEvent.setToBankgoodNumber("BG12345678");
        testEvent.setAmount(new BigDecimal("100.00"));
        testEvent.setStatus(TransactionStatus.PENDING);
        testEvent.setCreatedAt(LocalDateTime.now());
        testEvent.setUpdatedAt(LocalDateTime.now());

        testMapping = new BankMapping(
                UUID.randomUUID(),
                "BG12345678",
                "000002",
                "ACC987654321",
                "Test Bank B"
        );
    }

    @Nested
    @DisplayName("handleOutgoingTransaction")
    class HandleOutgoingTransaction {

        @Test
        @DisplayName("Should process transaction and create forwarded event when mapping exists")
        void handleOutgoing_WithValidMapping() throws Exception {
            // Given
            when(outgoingRepo.findById(testTransactionId)).thenReturn(Optional.empty());
            when(outgoingRepo.save(any(OutgoingTransaction.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(mappingRepo.findByBankgoodNumber("BG12345678"))
                    .thenReturn(Optional.of(testMapping));
            when(outboxEventRepo.save(any(OutboxEvent.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // When
            transactionService.handleOutgoingTransaction(testEvent);

            // Then
            verify(outgoingRepo, times(1)).save(any(OutgoingTransaction.class));
            
            ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
            verify(outboxEventRepo, times(1)).save(outboxCaptor.capture());
            
            OutboxEvent savedEvent = outboxCaptor.getValue();
            assertThat(savedEvent.getTopic()).isEqualTo("transactions.forwarded");
            assertThat(savedEvent.getTransactionId()).isEqualTo(testTransactionId);
        }

        @Test
        @DisplayName("Should create failed event when mapping not found")
        void handleOutgoing_MappingNotFound() throws Exception {
            // Given
            when(outgoingRepo.findById(testTransactionId)).thenReturn(Optional.empty());
            when(outgoingRepo.save(any(OutgoingTransaction.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(mappingRepo.findByBankgoodNumber("BG12345678"))
                    .thenReturn(Optional.empty());
            when(outboxEventRepo.save(any(OutboxEvent.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // When
            transactionService.handleOutgoingTransaction(testEvent);

            // Then
            ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
            verify(outboxEventRepo, times(1)).save(outboxCaptor.capture());
            
            OutboxEvent savedEvent = outboxCaptor.getValue();
            assertThat(savedEvent.getTopic()).isEqualTo("transactions.completed");
            assertThat(savedEvent.getPayload()).contains("FAILED");
        }

        @Test
        @DisplayName("Should skip processing if transaction already exists")
        void handleOutgoing_DuplicateTransaction() {
            // Given
            OutgoingTransaction existing = new OutgoingTransaction();
            existing.setTransactionId(testTransactionId);
            when(outgoingRepo.findById(testTransactionId)).thenReturn(Optional.of(existing));

            // When
            transactionService.handleOutgoingTransaction(testEvent);

            // Then
            verify(outgoingRepo, never()).save(any());
            verify(outboxEventRepo, never()).save(any());
        }
    }

    @Nested
    @DisplayName("handleProcessedTransaction")
    class HandleProcessedTransaction {

        @Test
        @DisplayName("Should update transaction status and create completed event")
        void handleProcessed_Success() throws Exception {
            // Given
            OutgoingTransaction existingTx = new OutgoingTransaction();
            existingTx.setTransactionId(testTransactionId);
            existingTx.setFromClearingNumber("000001");
            existingTx.setStatus(TransactionStatus.PENDING);

            TransactionResponseEvent responseEvent = new TransactionResponseEvent(
                    testTransactionId,
                    TransactionStatus.SUCCESS,
                    "Transaction completed"
            );

            when(outgoingRepo.findById(testTransactionId)).thenReturn(Optional.of(existingTx));
            when(outgoingRepo.save(any(OutgoingTransaction.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(outboxEventRepo.existsByTransactionIdAndTopic(testTransactionId, "transactions.completed"))
                    .thenReturn(false);
            when(outboxEventRepo.save(any(OutboxEvent.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // When
            transactionService.handleProcessedTransaction(responseEvent);

            // Then
            ArgumentCaptor<OutgoingTransaction> txCaptor = ArgumentCaptor.forClass(OutgoingTransaction.class);
            verify(outgoingRepo, times(1)).save(txCaptor.capture());
            assertThat(txCaptor.getValue().getStatus()).isEqualTo(TransactionStatus.SUCCESS);

            verify(outboxEventRepo, times(1)).save(any(OutboxEvent.class));
        }

        @Test
        @DisplayName("Should skip if transaction not found")
        void handleProcessed_TransactionNotFound() {
            // Given
            TransactionResponseEvent responseEvent = new TransactionResponseEvent(
                    testTransactionId,
                    TransactionStatus.SUCCESS,
                    "Transaction completed"
            );
            when(outgoingRepo.findById(testTransactionId)).thenReturn(Optional.empty());

            // When
            transactionService.handleProcessedTransaction(responseEvent);

            // Then
            verify(outgoingRepo, never()).save(any());
            verify(outboxEventRepo, never()).save(any());
        }

        @Test
        @DisplayName("Should not create duplicate outbox event")
        void handleProcessed_NoDuplicateOutbox() throws Exception {
            // Given
            OutgoingTransaction existingTx = new OutgoingTransaction();
            existingTx.setTransactionId(testTransactionId);
            existingTx.setFromClearingNumber("000001");
            existingTx.setStatus(TransactionStatus.PENDING);

            TransactionResponseEvent responseEvent = new TransactionResponseEvent(
                    testTransactionId,
                    TransactionStatus.SUCCESS,
                    "Transaction completed"
            );

            when(outgoingRepo.findById(testTransactionId)).thenReturn(Optional.of(existingTx));
            when(outgoingRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(outboxEventRepo.existsByTransactionIdAndTopic(testTransactionId, "transactions.completed"))
                    .thenReturn(true); // Already exists

            // When
            transactionService.handleProcessedTransaction(responseEvent);

            // Then
            verify(outgoingRepo, times(1)).save(any());
            verify(outboxEventRepo, never()).save(any()); // Should not save duplicate
        }
    }

    @Nested
    @DisplayName("getOutgoingTransactionById")
    class GetOutgoingTransactionById {

        @Test
        @DisplayName("Should return transaction when found")
        void getById_Found() {
            // Given
            OutgoingTransaction tx = new OutgoingTransaction();
            tx.setTransactionId(testTransactionId);
            tx.setStatus(TransactionStatus.PENDING);
            
            when(outgoingRepo.findByTransactionId(testTransactionId)).thenReturn(Optional.of(tx));

            // When
            ResponseEntity<?> response = transactionService.getOutgoingTransactionById(testTransactionId);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEqualTo(tx);
        }

        @Test
        @DisplayName("Should return 404 when transaction not found")
        void getById_NotFound() {
            // Given
            when(outgoingRepo.findByTransactionId(testTransactionId)).thenReturn(Optional.empty());

            // When
            ResponseEntity<?> response = transactionService.getOutgoingTransactionById(testTransactionId);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }
}
