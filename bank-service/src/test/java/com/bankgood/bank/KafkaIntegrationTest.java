package com.bankgood.bank;

import com.bankgood.common.kafka.TestKafkaConsumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import com.bankgood.bank.kafka.PaymentConsumer;
import com.bankgood.bank.kafka.PaymentProducer;
import com.bankgood.common.config.TestKafkaConfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import org.springframework.test.context.TestPropertySource;
import org.springframework.context.annotation.Import;

@SpringBootTest
@EmbeddedKafka(
    partitions = 1,
    brokerProperties = { "listeners=PLAINTEXT://localhost:9093", "port=9093" },
    topics = { "payment.requests", "payment.prepare" }
)
@TestPropertySource(properties = {
    "kafka.enabled=true",
    "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
    "spring.kafka.consumer.group-id=test-group",
    "spring.kafka.consumer.auto-offset-reset=earliest"
})
@Import(TestKafkaConfig.class)
public class KafkaIntegrationTest {

    @Autowired
    private PaymentProducer paymentProducer;

    @SpyBean
    private PaymentConsumer paymentConsumer;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    private TestKafkaConsumer<Object> testConsumer;

    @BeforeEach
    void setUp() {
        testConsumer = new TestKafkaConsumer<>(embeddedKafkaBroker, "payment.requests", Object.class);
    }

    @AfterEach
    void tearDown() {
        testConsumer.close();
    }

    @Test
    @Timeout(5) 
    void testPaymentProducer_sendsMessageCorrectly() {

        String message = "Test transaction";

        paymentProducer.sendMessage(message);

        ConsumerRecord<String, Object> singleRecord = KafkaTestUtils.getSingleRecord(testConsumer.getConsumer(), "payment.requests");
        assertThat(singleRecord).isNotNull();
        assertThat(singleRecord.value()).isEqualTo(message);
    }

    @Test
    @Timeout(10)
    void testPaymentConsumer_receivesMessage() throws Exception {
        String testMessage = "Prepare payment 123";
        kafkaTemplate.send("payment.prepare", testMessage).get(); 

        Thread.sleep(1000);

        verify(paymentConsumer, timeout(8000).atLeastOnce()).consume(testMessage);
    }
}