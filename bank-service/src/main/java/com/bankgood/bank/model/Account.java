package com.bankgood.bank.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "accounts")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true, nullable = false)
    private String accountNumber;

    @Column(nullable = false)
    private String accountHolder;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal balance;
    
    // Uses optimistic locking to prevent race conditions when two requests tries to
    // access the same account at the same time
    @Version
    private Long version;
}