package com.proyecto.servicios.dto.response;

import com.proyecto.servicios.enums.Catalogos.MetodoA2F;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Datos para registrar la cuenta en la app autenticadora: escanear {@code uriOtpauth} como QR
 * o capturar {@code secreto} a mano.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class A2fConfiguracionResponse {

    private MetodoA2F metodo;
    private String secreto;
    private String uriOtpauth;
}
