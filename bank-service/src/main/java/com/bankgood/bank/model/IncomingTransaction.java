package com.bankgood.bank.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "incoming_transactions")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class IncomingTransaction {

    @Id
    @Column(unique = true, updatable = false, nullable = false)
    private UUID transactionId;

    @Column(nullable = false)
    private String toClearingNumber;

    @Column(nullable = false)
    private String toAccountNumber;

    @Column(nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionStatus status = TransactionStatus.PENDING; // PENDING, SUCCESS, FAILED

    @CreationTimestamp
    @Column(nullable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public IncomingTransaction(UUID transactionId, String toClearingNumber, String toAccountNumber,
            BigDecimal amount, TransactionStatus status) {
        this.transactionId = transactionId;
        this.toClearingNumber = toClearingNumber;
        this.toAccountNumber = toAccountNumber;
        this.amount = amount;
        this.status = status;
    }
}
