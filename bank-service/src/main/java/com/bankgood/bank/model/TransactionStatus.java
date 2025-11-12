package com.bankgood.bank.model;

public enum TransactionStatus {
    
    // // Initial state - money reserved on sender's account
    // INITIATED,
    
    // // Event published to Kafka, waiting for switch to route
    // SENT_TO_SWITCH,
    
    // // Switch routed to receiving bank, waiting for confirmation
    // PENDING_AT_RECEIVER,
    
    // // Receiving bank confirmed funds added
    // COMPLETED,
    
    // // Something went wrong at any stage
    // FAILED,
    
    // // Money refunded to sender after timeout or failure
    // REFUNDED

    PENDING,

    FAILED,

    SUCCESS
}