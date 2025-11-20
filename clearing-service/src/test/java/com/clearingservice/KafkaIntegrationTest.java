package com.clearingservice;

import com.bankgood.common.config.TestKafkaConfig;
import com.bankgood.common.event.TransactionEvent;
import com.bankgood.common.event.TransactionResponseEvent;
import com.bankgood.common.kafka.TestKafkaConsumer;
import com.clearingservice.model.BankMapping;
import com.clearingservice.model.Transaction;
import com.bankgood.common.model.TransactionStatus;
import com.clearingservice.repository.BankMappingRepository;
import com.clearingservice.repository.TransactionRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@EmbeddedKafka(
        partitions = 1,
        brokerProperties = {"listeners=PLAINTEXT://localhost:9094", "port=9094"},
        topics = {"transactions.outgoing", "transactions.response", "transactions.incoming.testbank"}
)
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.consumer.group-id=test-group",
        "spring.kafka.consumer.auto-offset-reset=earliest"
})
@Import(TestKafkaConfig.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class KafkaIntegrationTest {

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @Autowired
    private KafkaTemplate<String, TransactionEvent> transactionKafkaTemplate;

    @Autowired
    private KafkaTemplate<String, TransactionResponseEvent> responseKafkaTemplate;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private BankMappingRepository bankMappingRepository;

    private TestKafkaConsumer<Object> testConsumer;

    @BeforeEach
    void setUp() {
        testConsumer = new TestKafkaConsumer<>(embeddedKafkaBroker, "transactions.response", Object.class);
        transactionRepository.deleteAll();
        bankMappingRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        testConsumer.close();
    }

    @Test
    @Order(1)
    @Timeout(5)
    void testProducer_sendsTransactionEventCorrectly() throws Exception {
        // Arrange: Subscribe test consumer to a clean topic
        testConsumer.getConsumer().unsubscribe();
        testConsumer.getConsumer().subscribe(java.util.Collections.singletonList("transactions.incoming.testbank"));

        TransactionEvent event = new TransactionEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "1234",
                "999888777",
                "9876", // toBankgoodNumber
                "9876", // toClearingNumber
                "111222333", // toAccountNumber
                BigDecimal.valueOf(75.00),
                TransactionStatus.PENDING,
                LocalDateTime.now(),
                LocalDateTime.now()
        );

        // Act: Send message using the producer directly (not through service)
        transactionKafkaTemplate.send("transactions.incoming.testbank", event).get();

        // Assert: Wait for message to be available and verify
        await()
                .atMost(5, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    org.apache.kafka.clients.consumer.ConsumerRecords<String, Object> records =
                            KafkaTestUtils.getRecords(testConsumer.getConsumer(), Duration.ofMillis(500));
                    assertThat(records.count()).isGreaterThan(0);
                });

        System.out.println("✓ Producer test passed - Message sent successfully!");
    }

    @Test
    @Order(2)
    @Timeout(10)
    void testTransactionEventListener_receivesAndProcessesTransaction() throws Exception {
        // Arrange: Create bank mapping so transaction processing succeeds
        BankMapping mapping = new BankMapping();
        mapping.setBankgoodNumber("12345");
        mapping.setClearingNumber("9876");
        mapping.setAccountNumber("111222333");
        mapping.setBankName("testbank");
        bankMappingRepository.save(mapping);

        TransactionEvent event = new TransactionEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "1234",
                "999888777",
                "12345", // toBankgoodNumber matches mapping
                "9876", // toClearingNumber
                "111222333", // toAccountNumber
                BigDecimal.valueOf(100.00),
                TransactionStatus.PENDING,
                LocalDateTime.now(),
                LocalDateTime.now()
        );

        // Act: Send event to transactions.outgoing topic
        transactionKafkaTemplate.send("transactions.outgoing", event).get();

        // Assert: Wait for transaction to be saved in database (proves listener was called)
        await()
                .atMost(10, TimeUnit.SECONDS)
                .pollInterval(200, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    Optional<Transaction> savedTx = transactionRepository.findByTransactionId(event.getTransactionId());
                    assertThat(savedTx).isPresent();
                    assertThat(savedTx.get().getStatus()).isEqualTo(TransactionStatus.PENDING);
                    assertThat(savedTx.get().getToClearingNumber()).isEqualTo("9876");
                    assertThat(savedTx.get().getToAccountNumber()).isEqualTo("111222333");
                });

        System.out.println("✓ TransactionEventListener test passed!");
    }

    @Test
    @Order(3)
    @Timeout(10)
    void testTransactionResponseListener_receivesResponse() throws Exception {
        // Arrange: Create transaction in database
        Transaction tx = new Transaction();
        tx.setTransactionId(UUID.randomUUID());
        tx.setFromClearingNumber("1234");
        tx.setFromAccountNumber("999888777");
        tx.setToClearingNumber("9876");
        tx.setToAccountNumber("111222333");
        tx.setAmount(BigDecimal.valueOf(50.00));
        tx.setStatus(TransactionStatus.PENDING);
        tx.setCreatedAt(LocalDateTime.now());
        tx.setUpdatedAt(LocalDateTime.now());
        transactionRepository.save(tx);

        TransactionResponseEvent response = new TransactionResponseEvent(
                tx.getTransactionId(),
                TransactionStatus.SUCCESS,
                "Transaction completed"
        );

        // Act: Send response to transactions.response topic
        responseKafkaTemplate.send("transactions.response", response).get();

        // Assert: Wait for transaction to be updated in database (proves listener was called)
        await()
                .atMost(10, TimeUnit.SECONDS)
                .pollInterval(200, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    Optional<Transaction> updatedTx = transactionRepository.findByTransactionId(tx.getTransactionId());
                    assertThat(updatedTx).isPresent();
                    assertThat(updatedTx.get().getStatus()).isEqualTo(TransactionStatus.SUCCESS);
                    assertThat(updatedTx.get().getMessage()).isEqualTo("Transaction completed");
                });

        System.out.println("✓ TransactionResponseListener test passed!");
    }

    @Test
    @Order(4)
    @Timeout(15)
    void testEndToEnd_successfulTransactionFlow() throws Exception {
        // Arrange: Create bank mapping for successful routing
        BankMapping mapping = new BankMapping();
        mapping.setBankgoodNumber("12345");
        mapping.setClearingNumber("9876");
        mapping.setAccountNumber("111222333");
        mapping.setBankName("testbank");
        bankMappingRepository.save(mapping);

        UUID txId = UUID.randomUUID();

        // Step 1: Bank A sends transaction to clearing service
        TransactionEvent incomingEvent = new TransactionEvent(
                txId,
                UUID.randomUUID(),
                "1234",
                "999888777",
                "12345", // Will be resolved to clearing 9876, account 111222333
                "9876", // toClearingNumber
                "111222333", // toAccountNumber
                BigDecimal.valueOf(200.00),
                TransactionStatus.PENDING,
                LocalDateTime.now(),
                LocalDateTime.now()
        );

        transactionKafkaTemplate.send("transactions.outgoing", incomingEvent).get();

        // Verify: Wait for transaction to be created with PENDING status
        await()
                .atMost(10, TimeUnit.SECONDS)
                .pollInterval(200, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    Optional<Transaction> pendingTx = transactionRepository.findByTransactionId(txId);
                    assertThat(pendingTx).isPresent();
                    assertThat(pendingTx.get().getStatus()).isEqualTo(TransactionStatus.PENDING);
                    assertThat(pendingTx.get().getToClearingNumber()).isEqualTo("9876");
                    assertThat(pendingTx.get().getToAccountNumber()).isEqualTo("111222333");
                });

        System.out.println("✓ Step 1: Transaction received from Bank A and forwarded to Bank B");

        // Step 2: Simulate Bank B sending success response
        TransactionResponseEvent bankBResponse = new TransactionResponseEvent(
                txId,
                TransactionStatus.SUCCESS,
                "Funds received successfully"
        );

        responseKafkaTemplate.send("transactions.response", bankBResponse).get();

        // Verify: Wait for transaction to be updated to SUCCESS
        await()
                .atMost(10, TimeUnit.SECONDS)
                .pollInterval(200, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    Optional<Transaction> successTx = transactionRepository.findByTransactionId(txId);
                    assertThat(successTx).isPresent();
                    assertThat(successTx.get().getStatus()).isEqualTo(TransactionStatus.SUCCESS);
                    assertThat(successTx.get().getMessage()).isEqualTo("Funds received successfully");
                });

        System.out.println("✓ Step 2: Response received from Bank B and forwarded to Bank A");

        // Verify: Response was sent back to Bank A on transactions.response topic
        org.apache.kafka.clients.consumer.ConsumerRecords<String, Object> records =
                KafkaTestUtils.getRecords(testConsumer.getConsumer(), Duration.ofSeconds(2));
        assertThat(records.count()).isGreaterThan(0);

        System.out.println("✓ End-to-end flow test passed!");
    }

    @Test
    @Order(5)
    @Timeout(10)
    void testErrorScenario_bankMappingNotFound() throws Exception {
        // Arrange: No bank mapping exists for the recipient
        UUID txId = UUID.randomUUID();
        TransactionEvent event = new TransactionEvent(
                txId,
                UUID.randomUUID(),
                "1234",
                "999888777",
                "99999", // Non-existent bankgood number
                null, // toClearingNumber
                null, // toAccountNumber
                BigDecimal.valueOf(100.00),
                TransactionStatus.PENDING,
                LocalDateTime.now(),
                LocalDateTime.now()
        );

        // Act: Send transaction
        transactionKafkaTemplate.send("transactions.outgoing", event).get();

        // Assert: Wait for transaction to be saved with FAILED status
        await()
                .atMost(10, TimeUnit.SECONDS)
                .pollInterval(200, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    Optional<Transaction> failedTx = transactionRepository.findByTransactionId(txId);
                    assertThat(failedTx).as("Transaction should be saved in database").isPresent();
                    assertThat(failedTx.get().getStatus()).isEqualTo(TransactionStatus.FAILED);
                    assertThat(failedTx.get().getMessage()).contains("not found");
                });

        System.out.println("✓ Error scenario test passed - Bank mapping not found handled correctly!");
    }

    @Test
    @Order(6)
    @Timeout(10)
    void testErrorScenario_transactionFailedAtBankB() throws Exception {
        // Arrange: Create bank mapping
        BankMapping mapping = new BankMapping();
        mapping.setBankgoodNumber("12345");
        mapping.setClearingNumber("9876");
        mapping.setAccountNumber("111222333");
        mapping.setBankName("testbank");
        bankMappingRepository.save(mapping);

        UUID txId = UUID.randomUUID();

        // Step 1: Send transaction
        TransactionEvent incomingEvent = new TransactionEvent(
                txId,
                UUID.randomUUID(),
                "1234",
                "999888777",
                "12345",
                "9876", // toClearingNumber
                "111222333", // toAccountNumber
                BigDecimal.valueOf(200.00),
                TransactionStatus.PENDING,
                LocalDateTime.now(),
                LocalDateTime.now()
        );

        transactionKafkaTemplate.send("transactions.outgoing", incomingEvent).get();

        // Verify: Wait for transaction to be PENDING
        await()
                .atMost(10, TimeUnit.SECONDS)
                .pollInterval(200, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    Optional<Transaction> pendingTx = transactionRepository.findByTransactionId(txId);
                    assertThat(pendingTx).isPresent();
                    assertThat(pendingTx.get().getStatus()).isEqualTo(TransactionStatus.PENDING);
                });

        // Step 2: Simulate Bank B responding with FAILED
        TransactionResponseEvent failureResponse = new TransactionResponseEvent(
                txId,
                TransactionStatus.FAILED,
                "Insufficient funds"
        );

        responseKafkaTemplate.send("transactions.response", failureResponse).get();

        // Assert: Wait for transaction to be updated to FAILED
        await()
                .atMost(10, TimeUnit.SECONDS)
                .pollInterval(200, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    Optional<Transaction> failedTx = transactionRepository.findByTransactionId(txId);
                    assertThat(failedTx).isPresent();
                    assertThat(failedTx.get().getStatus()).isEqualTo(TransactionStatus.FAILED);
                    assertThat(failedTx.get().getMessage()).isEqualTo("Insufficient funds");
                });

        System.out.println("✓ Error scenario test passed - Bank B failure handled correctly!");
    }
}
