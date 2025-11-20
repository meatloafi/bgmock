package com.bankgood.common.event;

import com.bankgood.common.model.TransactionStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Setter
@Getter
public class OutgoingTransactionEvent {
    private UUID transactionId;
    private UUID fromAccountId;
    private String fromClearingNumber;
    private String fromAccountNumber;
    private String toBankgoodNumber;
    private String toClearingNumber;
    private String toAccountNumber;
    private BigDecimal amount;
    private TransactionStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
