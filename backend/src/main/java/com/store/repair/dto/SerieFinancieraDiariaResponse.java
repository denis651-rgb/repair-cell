package com.store.repair.dto;

public record SerieFinancieraDiariaResponse(
        String fecha,
        double ingresos,
        double egresos,
        double balance,
        double ventas,
        double compras,
        double reparaciones
) {
}
