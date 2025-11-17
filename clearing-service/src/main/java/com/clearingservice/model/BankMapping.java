package com.clearingservice.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "bank_mappings")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BankMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String bankgoodNumber; // Bankgood number sent from bank-service

    @Column(nullable = false)
    private String clearingNumber;  // Bank's clearing number

    @Column(nullable = false)
    private String accountNumber;   // Recipient's account number

    @Column(nullable = false)
    private String bankName;        // Bank name (optional, but practical)
}

