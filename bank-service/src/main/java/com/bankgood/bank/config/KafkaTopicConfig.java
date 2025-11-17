package com.bankgood.bank.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
@ConditionalOnProperty(name = "kafka.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaTopicConfig {

    @Bean
    public NewTopic paymentRequestsTopic() {
        return TopicBuilder.name("payment.requests").build();
    }

    @Bean
    public NewTopic paymentPrepareTopic() {
        return TopicBuilder.name("payment.prepare").build();
    }

    @Bean
    public NewTopic paymentCommitTopic() {
        return TopicBuilder.name("payment.commit").build();
    }

    @Bean
    public NewTopic paymentRollbackTopic() {
        return TopicBuilder.name("payment.rollback").build();
    }
}