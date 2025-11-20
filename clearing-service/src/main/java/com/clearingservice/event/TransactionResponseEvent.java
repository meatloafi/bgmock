package com.clearingservice.event;

import com.clearingservice.model.TransactionStatus;
import lombok.*;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Setter
@Getter
public class TransactionResponseEvent {
    private UUID transactionId;
    private TransactionStatus status;
    private String message;
}
