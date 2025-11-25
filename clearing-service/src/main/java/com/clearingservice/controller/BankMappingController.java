package com.clearingservice.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.clearingservice.model.BankMapping;
import com.clearingservice.service.BankMappingService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;


@RestController
@RequestMapping("/clearing/bank-mapping")
public class BankMappingController {
    private final BankMappingService bankMappingService;

    public BankMappingController(BankMappingService bankMappingService) {
        this.bankMappingService = bankMappingService;
    }

    // ================= CREATE =================
    @PostMapping
    public ResponseEntity<?> createBankMapping(@RequestBody BankMapping bankMapping) {
        BankMapping created = bankMappingService.createBankMapping(bankMapping);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    // ================= READ =================
    @GetMapping("/{bankgoodNumber}")
    public ResponseEntity<?> fetchBankMapping(@PathVariable String bankgoodNumber) {
        return bankMappingService.fetchBankMapping(bankgoodNumber)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ================= UPDATE =================
    @PutMapping("/{bankgoodNumber}")
    public ResponseEntity<?> updateBankMapping(
            @PathVariable String bankgoodNumber,
            @RequestBody BankMapping updatedMapping) {
        return bankMappingService.updateBankMapping(bankgoodNumber, updatedMapping)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ================= DELETE =================
    @DeleteMapping("/{bankgoodNumber}")
    public ResponseEntity<?> deleteBankMapping(@PathVariable String bankgoodNumber) {
        boolean deleted = bankMappingService.deleteBankMapping(bankgoodNumber);
        if (deleted) {
            return ResponseEntity.noContent().build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }
}
