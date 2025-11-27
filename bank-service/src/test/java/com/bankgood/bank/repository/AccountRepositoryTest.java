package com.bankgood.bank.repository;

import com.bankgood.bank.model.Account;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for AccountRepository.
 * Uses @DataJpaTest which auto-configures an in-memory database.
 */
@DataJpaTest
@ActiveProfiles("test")
class AccountRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private AccountRepository accountRepository;

    private Account testAccount;

    @BeforeEach
    void setUp() {
        testAccount = new Account();
        testAccount.setAccountNumber("ACC123456789");
        testAccount.setAccountHolder("John Doe");
        testAccount.setBalance(new BigDecimal("1000.00"));
    }

    @Nested
    @DisplayName("findByAccountNumber")
    class FindByAccountNumber {

        @Test
        @DisplayName("Should find account by account number")
        void findByAccountNumber_Found() {
            // Given
            entityManager.persistAndFlush(testAccount);

            // When
            Optional<Account> result = accountRepository.findByAccountNumber("ACC123456789");

            // Then
            assertThat(result).isPresent();
            assertThat(result.get().getAccountHolder()).isEqualTo("John Doe");
            assertThat(result.get().getBalance()).isEqualByComparingTo(new BigDecimal("1000.00"));
        }

        @Test
        @DisplayName("Should return empty when account number not found")
        void findByAccountNumber_NotFound() {
            // When
            Optional<Account> result = accountRepository.findByAccountNumber("NONEXISTENT");

            // Then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("updateBalance")
    class UpdateBalance {

        @Test
        @DisplayName("Should update account balance")
        void updateBalance_Success() {
            // Given
            Account savedAccount = entityManager.persistAndFlush(testAccount);
            UUID accountId = savedAccount.getAccountId();

            // When
            accountRepository.updateBalance(accountId, new BigDecimal("2000.00"));
            entityManager.clear(); // Clear persistence context to force reload

            // Then
            Account updated = entityManager.find(Account.class, accountId);
            assertThat(updated.getBalance()).isEqualByComparingTo(new BigDecimal("2000.00"));
        }
    }

    @Nested
    @DisplayName("Basic CRUD")
    class BasicCrud {

        @Test
        @DisplayName("Should save and retrieve account")
        void saveAndFind() {
            // When
            Account saved = accountRepository.save(testAccount);

            // Then
            assertThat(saved.getAccountId()).isNotNull();

            Optional<Account> found = accountRepository.findById(saved.getAccountId());
            assertThat(found).isPresent();
            assertThat(found.get().getAccountNumber()).isEqualTo("ACC123456789");
        }

        @Test
        @DisplayName("Should delete account")
        void delete() {
            // Given
            Account saved = entityManager.persistAndFlush(testAccount);
            UUID accountId = saved.getAccountId();

            // When
            accountRepository.deleteById(accountId);

            // Then
            Optional<Account> found = accountRepository.findById(accountId);
            assertThat(found).isEmpty();
        }

        @Test
        @DisplayName("Should find all accounts")
        void findAll() {
            // Given
            entityManager.persistAndFlush(testAccount);

            Account account2 = new Account();
            account2.setAccountNumber("ACC987654321");
            account2.setAccountHolder("Jane Doe");
            account2.setBalance(new BigDecimal("500.00"));
            entityManager.persistAndFlush(account2);

            // When
            var accounts = accountRepository.findAll();

            // Then
            assertThat(accounts).hasSize(2);
        }
    }

    @Nested
    @DisplayName("Constraints")
    class Constraints {

        @Test
        @DisplayName("Should enforce unique account number")
        void uniqueAccountNumber() {
            // Given
            entityManager.persistAndFlush(testAccount);

            Account duplicate = new Account();
            duplicate.setAccountNumber("ACC123456789"); // Same number
            duplicate.setAccountHolder("Jane Doe");
            duplicate.setBalance(new BigDecimal("500.00"));

            // When & Then
            org.junit.jupiter.api.Assertions.assertThrows(
                    Exception.class,
                    () -> {
                        entityManager.persistAndFlush(duplicate);
                    }
            );
        }
    }
}
