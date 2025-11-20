package com.bankgood.bank.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;


@Entity
@Table(name = "accounts")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Setter
@Getter
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(unique = true, updatable = false, nullable = false)
    private UUID accountId;


    @Column(unique = true, nullable = false)
    @NotBlank(message = "Account number is required")
    private String accountNumber;

    @Column(nullable = false)
    @NotBlank(message = "Account holder is required")
    private String accountHolder;

    @Column(nullable = false)
    @DecimalMin(value = "0.0", inclusive = true, message = "Balance cannot be negative")
    private BigDecimal balance;


    // Uses optimistic locking to prevent race conditions when two requests tries to
    // access the same account at the same time
    @Version
    private Long version;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}