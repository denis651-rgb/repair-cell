package com.store.repair.util;

import com.store.repair.domain.OrdenReparacion;
import com.store.repair.domain.ParteOrdenReparacion;

import java.util.List;

public final class OrdenMontoUtils {

    private OrdenMontoUtils() {
    }

    public static double resolveMontoVisible(OrdenReparacion orden) {
        if (orden == null) {
            return 0D;
        }

        double costoFinal = safeDouble(orden.getCostoFinal());
        if (costoFinal > 0) {
            return costoFinal;
        }

        double montoPartes = resolveMontoPartes(orden.getPartes());
        if (montoPartes > 0) {
            return montoPartes;
        }

        double costoEstimado = safeDouble(orden.getCostoEstimado());
        if (costoEstimado > 0) {
            return costoEstimado;
        }

        return 0D;
    }

    public static double resolveMontoPartes(List<ParteOrdenReparacion> partes) {
        if (partes == null || partes.isEmpty()) {
            return 0D;
        }

        return partes.stream()
                .mapToDouble(OrdenMontoUtils::resolveParteSubtotal)
                .sum();
    }

    public static double resolveParteSubtotal(ParteOrdenReparacion parte) {
        return resolvePartePrecioUnitario(parte) * safeInt(parte == null ? null : parte.getCantidad());
    }

    public static double resolvePartePrecioUnitario(ParteOrdenReparacion parte) {
        if (parte == null) {
            return 0D;
        }

        double precioUnitario = safeDouble(parte.getPrecioUnitario());
        if (precioUnitario > 0) {
            return precioUnitario;
        }

        if (parte.getProducto() != null) {
            double precioVenta = safeDouble(parte.getProducto().getPrecioVenta());
            if (precioVenta > 0) {
                return precioVenta;
            }
        }

        return 0D;
    }

    private static double safeDouble(Double value) {
        return value == null ? 0D : value;
    }

    private static int safeInt(Integer value) {
        return value == null ? 0 : value;
    }
}
