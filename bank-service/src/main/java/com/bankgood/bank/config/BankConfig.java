package com.bankgood.bank.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class BankConfig {
    
    @Value("${BANK_CLEARING_NUMBER}")
    private String clearingNumber;
    
    public String getClearingNumber() {
        return clearingNumber;
    }
}