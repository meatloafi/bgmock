package com.bankgood.bank.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import lombok.Getter;

@Component
@Getter
public class BankConfig {
    
    @Value("${BANK_CLEARING_NUMBER}")
    private String clearingNumber;
}