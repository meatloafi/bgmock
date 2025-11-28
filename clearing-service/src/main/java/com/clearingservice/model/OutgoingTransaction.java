package com.clearingservice.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "outgoing_transactions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Setter
@Getter
public class OutgoingTransaction {

    @Id
    // @GeneratedValue(strategy = GenerationType.UUID)
    @Column(unique = true, updatable = false, nullable = false)
    private UUID transactionId;

    @Column(nullable = false)
    private String fromClearingNumber;

    @Column(nullable = false)
    private String fromAccountNumber;

    @Column(nullable = false)
    private String toBankgoodNumber;

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

    public OutgoingTransaction(UUID transactionId, String fromClearingNumber,
            String fromAccountNumber,
            String toBankgoodNumber, BigDecimal amount) {
        this.transactionId = transactionId;
        this.fromClearingNumber = fromClearingNumber;
        this.fromAccountNumber = fromAccountNumber;
        this.toBankgoodNumber = toBankgoodNumber;
        this.amount = amount;
    }
}
