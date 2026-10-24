package com.proyecto.servicios.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Sesion iniciada: el token de acceso se envia en {@code Authorization: Bearer <tokenAcceso>}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TokenResponse {

    private String tokenAcceso;
    private String tipoToken;
    private Long expiraEnSegundos;
    private String nombreUsuario;
    private String numeroCuenta;
}
