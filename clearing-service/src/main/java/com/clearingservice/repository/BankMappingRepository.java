package com.clearingservice.repository;

import com.clearingservice.model.BankMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface BankMappingRepository extends JpaRepository<BankMapping, UUID> {
    Optional<BankMapping> findByBankgoodNumber(String bankgoodNumber);
    boolean existsByBankgoodNumber(String bankgoodNumber);
}

