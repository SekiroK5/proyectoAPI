package com.proyecto.servicios.handler;

import org.springframework.http.HttpStatus;

import java.util.Arrays;

/**
 * Traduce los codigos de rechazo de sendTx a mensajes y codigos HTTP propios. La documentacion de PuntoRed
 * pide mapear los casos de error del lado del integrador; el texto original de GestoPago solo se guarda en
 * la bitacora (tabla transacciones), porque puede referirse a datos internos como el saldo del distribuidor.
 */
public enum RechazoGestoPago {

    /** "Saldo insuficiente": es el saldo del distribuidor con PuntoRed, no el del cliente. */
    SALDO_DISTRIBUIDOR("02", HttpStatus.SERVICE_UNAVAILABLE,
            "El servicio de pagos no está disponible por el momento. Intente más tarde."),

    /** "Transaccion aplicada anteriormente": mismo servicio y monto dentro de la ventana de duplicados. */
    DUPLICADA("06", HttpStatus.CONFLICT,
            "Ya se realizó una operación igual recientemente (mismo servicio y monto). Verifique su historial antes de reintentar."),

    OTRO(null, HttpStatus.UNPROCESSABLE_ENTITY,
            "El proveedor rechazó la operación. Verifique el teléfono, la referencia o el monto e intente de nuevo.");

    private final String codigo;
    private final HttpStatus status;
    private final String mensaje;

    RechazoGestoPago(String codigo, HttpStatus status, String mensaje) {
        this.codigo = codigo;
        this.status = status;
        this.mensaje = mensaje;
    }

    public static RechazoGestoPago de(String codigo) {
        return Arrays.stream(values())
                .filter(r -> r.codigo != null && r.codigo.equals(codigo))
                .findFirst()
                .orElse(OTRO);
    }

    public HttpStatus status() {
        return status;
    }

    /** Mensaje para el cliente; incluye el codigo de GestoPago como referencia para soporte. */
    public String mensaje(String codigoGestoPago) {
        return mensaje + " No se realizó ningún cargo. (Código GP-" + codigoGestoPago + ")";
    }
}
