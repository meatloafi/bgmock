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
    private String bankgoodNumber; // bankgironumret som skickas från bank-service

    @Column(nullable = false)
    private String clearingNumber;  // bankens clearingnummer

    @Column(nullable = false)
    private String accountNumber;   // mottagarens kontonummer

    @Column(nullable = false)
    private String bankName;        // namn på bank (valfritt, men praktiskt)
}

