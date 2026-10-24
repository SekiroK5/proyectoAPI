package com.proyecto.servicios.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Respuesta uniforme de la API: hereda {@code codigo} y {@code mensaje} de GenericResponse
 * (codigo 0 = exito, 1 = error) y agrega los {@code datos} devueltos.
 * Los errores de negocio (no encontrado, duplicado, etc.) se devuelven con este mismo objeto;
 * {@code status} solo le indica al controlador el codigo HTTP y no se serializa.
 *
 * @param <T> Tipo del contenido de {@code datos}
 */
@Getter
@JsonPropertyOrder({"codigo", "mensaje", "datos"})
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> extends GenericResponse {

    public static final int EXITO = 0;
    public static final int ERROR = 1;

    private final T datos;

    @JsonIgnore
    private final HttpStatus status;

    private ApiResponse(int codigo, String mensaje, T datos, HttpStatus status) {
        setCodigo(codigo);
        setMensaje(mensaje);
        this.datos = datos;
        this.status = status;
    }

    public static <T> ApiResponse<T> exito(HttpStatus status, String mensaje, T datos) {
        return new ApiResponse<>(EXITO, mensaje, datos, status);
    }

    public static <T> ApiResponse<T> error(HttpStatus status, String mensaje) {
        return new ApiResponse<>(ERROR, mensaje, null, status);
    }
}
