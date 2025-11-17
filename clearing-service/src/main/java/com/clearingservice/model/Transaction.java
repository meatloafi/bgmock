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
@Table(name = "transactions")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private UUID transactionId; // samma som i TransactionDTO

    @Column(nullable = false)
    private String fromAccountNumber;

    @Column(nullable = false)
    private String fromClearingNumber;

    @Column(nullable = true)  // Can be null when recipient mapping not found
    private String toClearingNumber;

    @Column(nullable = true)  // Can be null when recipient mapping not found
    private String toAccountNumber;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionStatus status; // PENDING, SUCCESS, FAILED

    private String failureReason;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
