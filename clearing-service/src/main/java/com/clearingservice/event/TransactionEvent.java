package com.clearingservice.event;

import com.clearingservice.model.TransactionStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransactionEvent {
    private UUID transactionId;
    private UUID fromAccountId;
    private String fromClearingNumber;
    private String fromAccountNumber;
    private String toClearingNumber; // Fylls via lookup
    private String toAccountNumber; // Fylls via lookup
    private BigDecimal amount;
    private TransactionStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
