package com.clearingservice.service;

import com.clearingservice.model.BankMapping;
import com.clearingservice.repository.BankMappingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for BankMappingService.
 */
@ExtendWith(MockitoExtension.class)
class BankMappingServiceTest {

    @Mock
    private BankMappingRepository repository;

    @InjectMocks
    private BankMappingService bankMappingService;

    private BankMapping testMapping;
    private final String testBankgoodNumber = "BG12345678";

    @BeforeEach
    void setUp() {
        testMapping = new BankMapping(
                UUID.randomUUID(),
                testBankgoodNumber,
                "000002",
                "ACC123456789",
                "Test Bank B"
        );
    }

    @Nested
    @DisplayName("createBankMapping")
    class CreateBankMapping {

        @Test
        @DisplayName("Should create bank mapping successfully")
        void createBankMapping_Success() {
            // Given
            BankMapping inputMapping = new BankMapping(
                    null, testBankgoodNumber, "000002", "ACC123456789", "Test Bank B"
            );
            when(repository.findByBankgoodNumber(testBankgoodNumber)).thenReturn(Optional.empty());
            when(repository.save(any(BankMapping.class))).thenReturn(testMapping);

            // When
            BankMapping result = bankMappingService.createBankMapping(inputMapping);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getBankgoodNumber()).isEqualTo(testBankgoodNumber);
            assertThat(result.getClearingNumber()).isEqualTo("000002");

            verify(repository, times(1)).findByBankgoodNumber(testBankgoodNumber);
            verify(repository, times(1)).save(inputMapping);
        }

        @Test
        @DisplayName("Should throw exception when bankgood number already exists")
        void createBankMapping_Duplicate() {
            // Given
            BankMapping inputMapping = new BankMapping(
                    null, testBankgoodNumber, "000002", "ACC123456789", "Test Bank B"
            );
            when(repository.findByBankgoodNumber(testBankgoodNumber))
                    .thenReturn(Optional.of(testMapping));

            // When & Then
            assertThatThrownBy(() -> bankMappingService.createBankMapping(inputMapping))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("already exists");

            verify(repository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("fetchBankMapping")
    class FetchBankMapping {

        @Test
        @DisplayName("Should return bank mapping when found")
        void fetchBankMapping_Found() {
            // Given
            when(repository.findByBankgoodNumber(testBankgoodNumber))
                    .thenReturn(Optional.of(testMapping));

            // When
            Optional<BankMapping> result = bankMappingService.fetchBankMapping(testBankgoodNumber);

            // Then
            assertThat(result).isPresent();
            assertThat(result.get().getBankgoodNumber()).isEqualTo(testBankgoodNumber);
            assertThat(result.get().getClearingNumber()).isEqualTo("000002");
        }

        @Test
        @DisplayName("Should return empty when bank mapping not found")
        void fetchBankMapping_NotFound() {
            // Given
            when(repository.findByBankgoodNumber("NONEXISTENT")).thenReturn(Optional.empty());

            // When
            Optional<BankMapping> result = bankMappingService.fetchBankMapping("NONEXISTENT");

            // Then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("updateBankMapping")
    class UpdateBankMapping {

        @Test
        @DisplayName("Should update bank mapping successfully")
        void updateBankMapping_Success() {
            // Given
            BankMapping updatedData = new BankMapping(
                    null, testBankgoodNumber, "000003", "ACC999999999", "Updated Bank"
            );
            BankMapping savedMapping = new BankMapping(
                    testMapping.getId(),
                    testBankgoodNumber,
                    "000003",
                    "ACC999999999",
                    "Updated Bank"
            );

            when(repository.findByBankgoodNumber(testBankgoodNumber))
                    .thenReturn(Optional.of(testMapping));
            when(repository.save(any(BankMapping.class))).thenReturn(savedMapping);

            // When
            Optional<BankMapping> result = bankMappingService.updateBankMapping(testBankgoodNumber, updatedData);

            // Then
            assertThat(result).isPresent();
            assertThat(result.get().getClearingNumber()).isEqualTo("000003");
            assertThat(result.get().getAccountNumber()).isEqualTo("ACC999999999");
            assertThat(result.get().getBankName()).isEqualTo("Updated Bank");

            verify(repository, times(1)).save(any(BankMapping.class));
        }

        @Test
        @DisplayName("Should return empty when bank mapping not found")
        void updateBankMapping_NotFound() {
            // Given
            BankMapping updatedData = new BankMapping(
                    null, "NONEXISTENT", "000003", "ACC999", "Bank"
            );
            when(repository.findByBankgoodNumber("NONEXISTENT")).thenReturn(Optional.empty());

            // When
            Optional<BankMapping> result = bankMappingService.updateBankMapping("NONEXISTENT", updatedData);

            // Then
            assertThat(result).isEmpty();
            verify(repository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("deleteBankMapping")
    class DeleteBankMapping {

        @Test
        @DisplayName("Should delete bank mapping and return true")
        void deleteBankMapping_Success() {
            // Given
            when(repository.findByBankgoodNumber(testBankgoodNumber))
                    .thenReturn(Optional.of(testMapping));
            doNothing().when(repository).delete(testMapping);

            // When
            boolean result = bankMappingService.deleteBankMapping(testBankgoodNumber);

            // Then
            assertThat(result).isTrue();
            verify(repository, times(1)).delete(testMapping);
        }

        @Test
        @DisplayName("Should return false when bank mapping not found")
        void deleteBankMapping_NotFound() {
            // Given
            when(repository.findByBankgoodNumber("NONEXISTENT")).thenReturn(Optional.empty());

            // When
            boolean result = bankMappingService.deleteBankMapping("NONEXISTENT");

            // Then
            assertThat(result).isFalse();
            verify(repository, never()).delete(any());
        }
    }
}
