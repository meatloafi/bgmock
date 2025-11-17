package com.bankgood.bank.event;

import com.bankgood.bank.model.TransactionStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransactionResponseEvent {
    private UUID transactionId;
    private TransactionStatus status;
    private String message;
}
