package com.clearingservice.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.clearingservice.model.BankMapping;
import com.clearingservice.service.BankMappingService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;



@RestController
@RequestMapping("/clearing/bank-mapping")
public class BankMappingController {
    private final BankMappingService bankMappingService;

    public BankMappingController(BankMappingService bankMappingService) {
        this.bankMappingService = bankMappingService;
    }

    @PostMapping
    public ResponseEntity<?> createBankMapping(@RequestBody BankMapping bankMapping) {
        BankMapping created = bankMappingService.createBankMapping(bankMapping);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{bankgoodNumber}")
    public ResponseEntity<?> fetchBankMapping(@RequestParam String bankgoodNumber) {
        return bankMappingService.fetchBankMapping(bankgoodNumber)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
    
    


}
