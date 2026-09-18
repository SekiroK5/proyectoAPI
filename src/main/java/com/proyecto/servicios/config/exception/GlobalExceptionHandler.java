package com.proyecto.servicios.config.exception;

import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IntegracionException.class)
    public ResponseEntity<Map<String, Object>> handleIntegracion(IntegracionException ex) {
        log.error("Error de integracion con servicio externo: {}", ex.getMessage());
        return ResponseEntity.status(ex.getStatus())
                .body(errorBody(ex.getStatus().value(), ex.getMessage()));
    }

    @ExceptionHandler(FeignException.Unauthorized.class)
    public ResponseEntity<Map<String, Object>> handleUnauthorized(FeignException.Unauthorized ex) {
        log.error("Token no valido o expirado al llamar servicio externo");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(errorBody(401, "Token no valido o expirado. Intente nuevamente en unos minutos."));
    }

    @ExceptionHandler(FeignException.Forbidden.class)
    public ResponseEntity<Map<String, Object>> handleForbidden(FeignException.Forbidden ex) {
        log.error("Acceso denegado (403) al servicio externo. El token puede haber expirado.");
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(errorBody(403, "Token expirado. El sistema renovara el token automaticamente."));
    }

    @ExceptionHandler(FeignException.GatewayTimeout.class)
    public ResponseEntity<Map<String, Object>> handleTimeout(FeignException.GatewayTimeout ex) {
        log.error("Timeout al conectar con el servicio externo");
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                .body(errorBody(504, "Timeout al conectar con el servicio externo. Intente nuevamente."));
    }

    @ExceptionHandler(FeignException.class)
    public ResponseEntity<Map<String, Object>> handleFeign(FeignException ex) {
        log.error("Error de comunicacion con servicio externo. Status: {}", ex.status());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(errorBody(502, "Error de comunicacion con el servicio externo."));
    }

    @ExceptionHandler(jakarta.xml.bind.JAXBException.class)
    public ResponseEntity<Map<String, Object>> handleJaxb(jakarta.xml.bind.JAXBException ex) {
        log.error("Error al parsear respuesta XML del servicio externo: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(errorBody(500, "Error al procesar la respuesta del servicio externo."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(Exception ex) {
        log.error("Error interno del servidor: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(errorBody(500, "Error interno del servidor."));
    }

    private Map<String, Object> errorBody(int codigo, String mensaje) {
        return Map.of(
                "codigo", codigo,
                "mensaje", mensaje
        );
    }
}
