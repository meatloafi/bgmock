package com.bankgood.bank.event;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Setter
@Getter
public class ReserveFundsResult {
    private boolean success;
    private String message;
    private AccountDTO account;
}