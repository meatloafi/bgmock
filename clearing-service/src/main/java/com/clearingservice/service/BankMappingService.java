package com.clearingservice.service;

import java.util.Optional;

import org.springframework.stereotype.Service;

import com.clearingservice.model.BankMapping;
import com.clearingservice.repository.BankMappingRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class BankMappingService {

    private final BankMappingRepository bankMappingRepository;

    public BankMappingService(BankMappingRepository bankMappingRepository) {
        this.bankMappingRepository = bankMappingRepository;
    }

    public BankMapping createBankMapping(BankMapping bankMapping) {
        // Check if bankgood number already exists
        if (bankMappingRepository.existsByBankgoodNumber(bankMapping.getBankgoodNumber())) {
            throw new IllegalArgumentException(
                "Bank-mapping already exists for Bankgood-number: " + bankMapping.getBankgoodNumber()
            );
        }
        return bankMappingRepository.save(bankMapping);
    }

    public Optional<BankMapping> fetchBankMapping(String bankgoodNumber) {
        return bankMappingRepository.findByBankgoodNumber(bankgoodNumber);
    }
}
