package com.clearingservice.event;

import com.clearingservice.model.TransactionStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Setter
@Getter
public class IncomingTransactionEvent {
    private UUID transactionId;
    private String toClearingNumber;
    private String toAccountNumber;
    private BigDecimal amount;
    private TransactionStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}