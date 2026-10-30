package com.proyecto.servicios.exception;

import com.proyecto.servicios.dto.response.ApiResponse;
import com.proyecto.servicios.dto.response.GenericResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;

/**
 * Manejo centralizado de excepciones en un unico metodo. Todas las respuestas usan el formato
 * {@code {codigo: 1, mensaje}} y nunca exponen trazas, nombres de clases ni mensajes internos:
 * el detalle tecnico solo va al log del servidor.
 * <p>
 * Los errores de negocio no llegan aqui: los servicios y handlers los devuelven en ApiResponse.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    public ResponseEntity<GenericResponse> manejar(Exception ex) {
        // Validaciones de los DTO (@Valid): se lista cada campo con su mensaje
        if (ex instanceof MethodArgumentNotValidException validacion) {
            Map<String, String> listError = new HashMap<>();
            for (FieldError fieldError : validacion.getBindingResult().getFieldErrors()) {
                listError.put(fieldError.getField(), fieldError.getDefaultMessage());
            }
            return responder(HttpStatus.BAD_REQUEST, "Datos no validos" + listError);
        }
        // JSON mal formado o con tipos incorrectos (p. ej. un texto donde va un numero)
        if (ex instanceof HttpMessageNotReadableException) {
            return responder(HttpStatus.BAD_REQUEST, "El cuerpo de la petición no es un JSON válido o tiene datos con un tipo incorrecto.");
        }
        // Errores lanzados a proposito con un mensaje ya pensado para el cliente
        if (ex instanceof ResponseStatusException conEstatus && conEstatus.getReason() != null) {
            return responder(conEstatus.getStatusCode(), conEstatus.getReason());
        }
        // Errores de Spring MVC que ya traen su codigo HTTP (405, 415, 404 de ruta inexistente, etc.)
        if (ex instanceof ErrorResponse errorSpring) {
            return responder(errorSpring.getStatusCode(), mensajePorEstatus(errorSpring.getStatusCode()));
        }
        // Cualquier otro error: el detalle solo al log
        log.error("Error no controlado: {}", ex.getClass().getName(), ex);
        return responder(HttpStatus.INTERNAL_SERVER_ERROR, "Ocurrió un error inesperado. Intente más tarde.");
    }

    private static String mensajePorEstatus(HttpStatusCode status) {
        return switch (status.value()) {
            case 404 -> "El recurso solicitado no existe.";
            case 405 -> "El método HTTP no está permitido para este recurso.";
            case 406 -> "El formato de respuesta solicitado no está disponible.";
            case 415 -> "El tipo de contenido no es compatible; envíe Content-Type: application/json.";
            default -> "La petición no se pudo procesar.";
        };
    }

    private static ResponseEntity<GenericResponse> responder(HttpStatusCode status, String mensaje) {
        GenericResponse genericResponse = new GenericResponse();
        genericResponse.setCodigo(ApiResponse.ERROR);
        genericResponse.setMensaje(mensaje);
        return new ResponseEntity<>(genericResponse, status);
    }
}
