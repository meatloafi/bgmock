package com.bankgood.bank.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionEvent {
    private UUID paymentId;
    private String fromAccountNumber;
    private String toAccountNumber;
    private String fromClearingNumber;
    private String toClearingNumber;
    private BigDecimal amount;
    private LocalDateTime eventTimestamp;
}