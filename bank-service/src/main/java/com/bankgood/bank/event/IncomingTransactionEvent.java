package com.bankgood.bank.event;

import com.bankgood.bank.model.TransactionStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class IncomingTransactionEvent {
    private UUID transactionId;
    private String toClearingNumber;
    private String toAccountNumber;
    private BigDecimal amount;
    private TransactionStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
