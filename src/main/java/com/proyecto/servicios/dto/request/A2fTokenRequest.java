package com.proyecto.servicios.dto.request;

import com.proyecto.servicios.util.Texto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;

/**
 * Base de los pasos del segundo factor: todos llevan el token temporal que devolvio el login.
 */
@Getter
public abstract class A2fTokenRequest {

    @Schema(description = "Token temporal devuelto por POST /api/v1/auth/login")
    @NotEmpty(message = "El token temporal es obligatorio.")
    private String tokenTemporal;

    public void setTokenTemporal(String tokenTemporal) {
        this.tokenTemporal = Texto.limpiar(tokenTemporal);
    }
}
