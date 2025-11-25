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
    private String bankgoodNumber; 

    @Column(nullable = false)
    private String clearingNumber;  

    @Column(nullable = false)
    private String accountNumber;   

    @Column(nullable = false)
    private String bankName;       
}

