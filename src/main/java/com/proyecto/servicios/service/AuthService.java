package com.proyecto.servicios.service;

import com.proyecto.servicios.dto.request.A2fConfiguracionRequest;
import com.proyecto.servicios.dto.request.A2fVerificacionRequest;
import com.proyecto.servicios.dto.request.LoginRequest;
import com.proyecto.servicios.dto.response.A2fConfiguracionResponse;
import com.proyecto.servicios.dto.response.ApiResponse;
import com.proyecto.servicios.dto.response.LoginResponse;
import com.proyecto.servicios.dto.response.TokenResponse;

/**
 * Inicio de sesion en dos pasos: contrasena y despues segundo factor (A2F).
 */
public interface AuthService {

    /** Valida usuario/correo + contrasena y devuelve un token temporal para el paso A2F. */
    ApiResponse<LoginResponse> iniciarSesion(LoginRequest request);

    /** Genera el secreto TOTP del cliente que aun no tiene segundo factor. */
    ApiResponse<A2fConfiguracionResponse> configurarA2F(A2fConfiguracionRequest request);

    /** Valida el codigo A2F (y lo activa la primera vez) y entrega el token de acceso. */
    ApiResponse<TokenResponse> verificarA2F(A2fVerificacionRequest request);
}
