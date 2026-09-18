package com.proyecto.servicios.config.exception;

import org.springframework.http.HttpStatus;

/**
 * Excepcion para errores de integracion con servicios externos.
 */
public class IntegracionException extends RuntimeException {

    private final HttpStatus status;

    public IntegracionException(String mensaje) {
        super(mensaje);
        this.status = HttpStatus.BAD_GATEWAY;
    }

    public IntegracionException(String mensaje, HttpStatus status) {
        super(mensaje);
        this.status = status;
    }

    public IntegracionException(String mensaje, Throwable causa) {
        super(mensaje, causa);
        this.status = HttpStatus.BAD_GATEWAY;
    }

    public IntegracionException(String mensaje, HttpStatus status, Throwable causa) {
        super(mensaje, causa);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
