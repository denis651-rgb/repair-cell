package com.store.repair.config;

public final class SanitizadorTexto {

    private SanitizadorTexto() {
    }

    public static String limpiar(String valor) {
        if (valor == null) {
            return null;
        }
        String limpio = valor.trim().replaceAll("\s+", " ");
        return limpio.isBlank() ? null : limpio;
    }
}
