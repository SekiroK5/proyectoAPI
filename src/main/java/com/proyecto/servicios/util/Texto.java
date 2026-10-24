package com.proyecto.servicios.util;

/**
 * Normalizacion de texto recibido del cliente (se aplica en los setters de los DTOs).
 */
public final class Texto {

    private Texto() {
    }

    /** Recorta espacios; una cadena vacia o solo con espacios se convierte en null. */
    public static String limpiar(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    public static String mayusculas(String valor) {
        String limpio = limpiar(valor);
        return limpio == null ? null : limpio.toUpperCase();
    }

    public static String minusculas(String valor) {
        String limpio = limpiar(valor);
        return limpio == null ? null : limpio.toLowerCase();
    }
}
