package com.store.repair.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class BalanceResponse {
    private Double entradas;
    private Double salidas;
    private Double balance;
}
