package com.proyecto.servicios.controller;

import com.proyecto.servicios.dto.request.A2fConfiguracionRequest;
import com.proyecto.servicios.dto.request.A2fVerificacionRequest;
import com.proyecto.servicios.dto.request.LoginRequest;
import com.proyecto.servicios.dto.response.A2fConfiguracionResponse;
import com.proyecto.servicios.dto.response.ApiResponse;
import com.proyecto.servicios.dto.response.GenericResponse;
import com.proyecto.servicios.dto.response.LoginResponse;
import com.proyecto.servicios.dto.response.TokenResponse;
import com.proyecto.servicios.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inicio de sesion en dos pasos. Flujo:
 * <ol>
 *   <li>POST /login con usuario o correo + contrasena: devuelve un token temporal y el siguiente paso.</li>
 *   <li>Solo la primera vez: POST /a2f/configuracion para obtener el secreto TOTP.</li>
 *   <li>POST /a2f/verificacion con el codigo de 6 digitos: devuelve el token de acceso.</li>
 * </ol>
 */
@Tag(name = "Inicio de sesión", description = "Usuario o correo + contraseña y segundo factor (TOTP)")
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Datos no válidos",
        content = @Content(schema = @Schema(implementation = GenericResponse.class)))
@RestController
@RequestMapping(value = "/api/v1/auth", produces = MediaType.APPLICATION_JSON_VALUE)
@Slf4j
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "Iniciar sesión (paso 1)",
            description = "Valida usuario o correo + contraseña. Respuestas: 200 (con token temporal), 400, 401, 403 (cliente dado de baja), 423 (bloqueo por intentos).")
    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<LoginResponse>> iniciarSesion(@Valid @RequestBody LoginRequest request) {
        log.info("POST /api/v1/auth/login");
        return responder(authService.iniciarSesion(request));
    }

    @Operation(summary = "Configurar segundo factor",
            description = "Devuelve el secreto TOTP y la URI otpauth:// para la app autenticadora. Respuestas: 200, 400, 401, 403, 409 (ya configurado), 423.")
    @PostMapping(value = "/a2f/configuracion", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<A2fConfiguracionResponse>> configurarA2F(@Valid @RequestBody A2fConfiguracionRequest request) {
        log.info("POST /api/v1/auth/a2f/configuracion");
        return responder(authService.configurarA2F(request));
    }

    @Operation(summary = "Verificar segundo factor (paso 2)",
            description = "Valida el código de 6 dígitos (la primera vez también activa el A2F) y devuelve el token de acceso. Respuestas: 200, 400, 401, 403, 409 (sin configurar), 423.")
    @PostMapping(value = "/a2f/verificacion", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<TokenResponse>> verificarA2F(@Valid @RequestBody A2fVerificacionRequest request) {
        log.info("POST /api/v1/auth/a2f/verificacion");
        return responder(authService.verificarA2F(request));
    }

    private static <T> ResponseEntity<ApiResponse<T>> responder(ApiResponse<T> respuesta) {
        return ResponseEntity.status(respuesta.getStatus()).body(respuesta);
    }
}
