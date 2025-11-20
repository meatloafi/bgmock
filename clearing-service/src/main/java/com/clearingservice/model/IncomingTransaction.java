package com.clearingservice.model;

import com.bankgood.common.model.TransactionStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "incoming_transactions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Setter
@Getter
public class IncomingTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
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
    private TransactionStatus status; // PENDING, SUCCESS, FAILED

    @CreationTimestamp
    @Column(nullable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public IncomingTransaction(String toClearingNumber, String toAccountNumber, BigDecimal amount) {
        this.toClearingNumber = toClearingNumber;
        this.toAccountNumber = toAccountNumber;
        this.amount = amount;
    }
}