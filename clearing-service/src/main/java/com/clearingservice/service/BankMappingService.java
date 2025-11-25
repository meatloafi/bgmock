package com.clearingservice.service;

import java.util.Optional;

import org.springframework.stereotype.Service;

import com.clearingservice.model.BankMapping;
import com.clearingservice.repository.BankMappingRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class BankMappingService {
    private final BankMappingRepository repository;

    public BankMappingService(BankMappingRepository repository) {
        this.repository = repository;
    }

    public BankMapping createBankMapping(BankMapping bankMapping) {
        // Optional: check if bankgoodNumber already exists
        if (repository.findByBankgoodNumber(bankMapping.getBankgoodNumber()).isPresent()) {
            throw new IllegalArgumentException("BankMapping with this bankgoodNumber already exists");
        }
        return repository.save(bankMapping);
    }

    public Optional<BankMapping> fetchBankMapping(String bankgoodNumber) {
        return repository.findByBankgoodNumber(bankgoodNumber);
    }

    public Optional<BankMapping> updateBankMapping(String bankgoodNumber, BankMapping updatedMapping) {
        return repository.findByBankgoodNumber(bankgoodNumber)
                .map(existing -> {
                    existing.setAccountNumber(updatedMapping.getAccountNumber());
                    existing.setClearingNumber(updatedMapping.getClearingNumber());
                    existing.setBankName(updatedMapping.getBankName());
                    return repository.save(existing);
                });
    }

    public boolean deleteBankMapping(String bankgoodNumber) {
        return repository.findByBankgoodNumber(bankgoodNumber)
                .map(mapping -> {
                    repository.delete(mapping);
                    return true;
                }).orElse(false);
    }
}
