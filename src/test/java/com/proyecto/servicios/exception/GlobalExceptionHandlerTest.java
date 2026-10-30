package com.proyecto.servicios.exception;

import com.proyecto.servicios.dto.response.GenericResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.server.ResponseStatusException;

import java.sql.SQLException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GlobalExceptionHandler - manejo centralizado sin exponer detalles internos")
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("JSON mal formado: 400 con mensaje propio, sin el detalle del parser")
    void jsonMalFormado() {
        ResponseEntity<GenericResponse> res = handler.manejar(new HttpMessageNotReadableException(
                "JSON parse error: Unexpected character at com.fasterxml...", new MockHttpInputMessage(new byte[0])));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(res.getBody().getCodigo()).isEqualTo(1);
        assertThat(res.getBody().getMensaje()).doesNotContain("fasterxml", "parse");
    }

    @Test
    @DisplayName("ResponseStatusException: respeta su código y su mensaje para el cliente")
    void responseStatus() {
        ResponseEntity<GenericResponse> res = handler.manejar(
                new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Error de comunicacion con el servicio de productos."));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(res.getBody().getMensaje()).isEqualTo("Error de comunicacion con el servicio de productos.");
    }

    @Test
    @DisplayName("Método no permitido: 405 con mensaje en español")
    void metodoNoPermitido() {
        ResponseEntity<GenericResponse> res = handler.manejar(
                new HttpRequestMethodNotSupportedException(HttpMethod.PUT.name(), List.of("POST")));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(res.getBody().getMensaje()).isEqualTo("El método HTTP no está permitido para este recurso.");
    }

    @Test
    @DisplayName("Error inesperado: 500 genérico, sin el mensaje interno (p. ej. SQL o credenciales)")
    void errorInesperado() {
        ResponseEntity<GenericResponse> res = handler.manejar(
                new IllegalStateException("password=12345678 jdbc:postgresql://localhost", new SQLException("tabla clientes")));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(res.getBody().getMensaje()).isEqualTo("Ocurrió un error inesperado. Intente más tarde.")
                .doesNotContain("password", "jdbc", "clientes");
    }
}
