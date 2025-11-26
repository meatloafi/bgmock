package com.clearingservice.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;


@Entity
@Table(name = "outbox_events")
@NoArgsConstructor
@AllArgsConstructor
@Data
@Getter
@Setter
public class OutboxEvent {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private UUID transactionId;
    
    @Column(nullable = false)
    private String topic;
    
    @Column(nullable = false)
    private String messageKey;
    
    @Column(columnDefinition = "TEXT", nullable = false)
    private String payload; // JSON string
    
    @Column(nullable = false)
    private boolean published = false;
    
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
    
    // Constructor
    public OutboxEvent(UUID transactionId, String topic, String messageKey, String payload) {
        this.transactionId = transactionId;
        this.topic = topic;
        this.messageKey = messageKey;
        this.payload = payload;
    }
    
    
    // Getters and setters
    public Long getId() {
        return id;
    }
    
    public UUID getTransactionId() {
        return transactionId;
    }
    
    public String getTopic() {
        return topic;
    }
    
    public String getMessageKey() {
        return messageKey;
    }
    
    public String getPayload() {
        return payload;
    }
    
    public boolean isPublished() {
        return published;
    }
    
    public void setPublished(boolean published) {
        this.published = published;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}