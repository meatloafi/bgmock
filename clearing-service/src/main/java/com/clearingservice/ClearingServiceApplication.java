package com.clearingservice;

import com.clearingservice.model.BankMapping;
import com.clearingservice.repository.BankMappingRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

 @SpringBootApplication
public class ClearingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ClearingServiceApplication.class, args);
    }

    @Bean
    CommandLineRunner initDatabase(BankMappingRepository repository) {
        return args -> {
            // Bank A Mapping (Alice)
            if (!repository.existsByBankgoodNumber("1111111111")) {
                repository.save(new BankMapping(null, "1111111111", "000001", "1111111111", "Bank A"));
            }
            
            // Bank B Mapping (Bob)
            if (!repository.existsByBankgoodNumber("2222222222")) {
                repository.save(new BankMapping(null, "2222222222", "000002", "2222222222", "Bank B"));
            }
            
            // Bank B Mapping (Charlie)
            if (!repository.existsByBankgoodNumber("3333333333")) {
                repository.save(new BankMapping(null, "3333333333", "000002", "3333333333", "Bank B"));
            }

            System.out.println("✅ Bank Mappings initialized in Database");
        };
    }
}