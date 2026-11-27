package com.proyecto.servicios.dto.request;

import lombok.NoArgsConstructor;

/**
 * Pide un codigo nuevo para confirmar el correo. Lleva el token temporal del login.
 */
@NoArgsConstructor
public class CorreoReenvioRequest extends A2fTokenRequest {
}
