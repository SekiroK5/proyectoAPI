package com.proyecto.servicios.service.impl;

import com.proyecto.servicios.dto.request.A2fConfiguracionRequest;
import com.proyecto.servicios.dto.request.A2fTokenRequest;
import com.proyecto.servicios.dto.request.A2fVerificacionRequest;
import com.proyecto.servicios.dto.request.LoginRequest;
import com.proyecto.servicios.dto.response.A2fConfiguracionResponse;
import com.proyecto.servicios.dto.response.ApiResponse;
import com.proyecto.servicios.dto.response.LoginResponse;
import com.proyecto.servicios.dto.response.TokenResponse;
import com.proyecto.servicios.entity.sf.Cliente;
import com.proyecto.servicios.enums.Catalogos.MetodoA2F;
import com.proyecto.servicios.enums.Catalogos.PasoLogin;
import com.proyecto.servicios.repository.sf.ClienteRepository;
import com.proyecto.servicios.seguridad.IntentosFallidos;
import com.proyecto.servicios.seguridad.TokenJwt;
import com.proyecto.servicios.seguridad.Totp;
import com.proyecto.servicios.service.AuthService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Igual que el resto de la API, los errores de negocio no usan excepciones: se responden con codigo 1
 * y el codigo HTTP correspondiente (401 credenciales, 403 cliente dado de baja, 409 estado A2F, 423 bloqueo).
 */
@Service
@Slf4j
public class AuthServiceImpl implements AuthService {

    static final String MSG_CREDENCIALES = "Usuario o contraseña incorrectos.";
    static final String MSG_TOKEN_TEMPORAL = "El token temporal no es válido o expiró. Inicie sesión de nuevo.";
    static final String MSG_CODIGO_INVALIDO = "El código de verificación no es válido o ya fue utilizado.";
    static final String MSG_CLIENTE_INACTIVO = "El cliente está dado de baja y no puede iniciar sesión.";
    static final String MSG_A2F_YA_CONFIGURADO = "El segundo factor ya está configurado. Envíe su código a /api/v1/auth/a2f/verificacion.";
    static final String MSG_A2F_SIN_CONFIGURAR = "Primero configure su segundo factor en /api/v1/auth/a2f/configuracion.";

    private final ClienteRepository clienteRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenJwt tokenJwt;
    private final Totp totp;
    private final IntentosFallidos intentosFallidos;
    private final String emisorA2F;
    /** Se compara contra este hash cuando el usuario no existe, para que la respuesta tarde lo mismo. */
    private final String hashFicticio;

    public AuthServiceImpl(ClienteRepository clienteRepository,
                           PasswordEncoder passwordEncoder,
                           TokenJwt tokenJwt,
                           Totp totp,
                           IntentosFallidos intentosFallidos,
                           @Value("${seguridad.a2f.emisor}") String emisorA2F) {
        this.clienteRepository = clienteRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenJwt = tokenJwt;
        this.totp = totp;
        this.intentosFallidos = intentosFallidos;
        this.emisorA2F = emisorA2F;
        this.hashFicticio = passwordEncoder.encode("contrasena-ficticia");
    }

    @Override
    @Transactional("sfTransactionManager")
    public ApiResponse<LoginResponse> iniciarSesion(LoginRequest request) {
        Optional<Cliente> encontrado = clienteRepository
                .findByNombreUsuarioOrCorreoElectronico(request.getUsuario(), request.getUsuario());
        if (encontrado.isEmpty() || encontrado.get().getContrasenaHash() == null) {
            passwordEncoder.matches(request.getContrasena(), hashFicticio);
            return ApiResponse.error(HttpStatus.UNAUTHORIZED, MSG_CREDENCIALES);
        }
        Cliente cliente = encontrado.get();
        if (intentosFallidos.estaBloqueado(cliente)) {
            return intentosFallidos.respuestaBloqueo(cliente);
        }
        if (!passwordEncoder.matches(request.getContrasena(), cliente.getContrasenaHash())) {
            return intentosFallidos.registrarFallo(cliente, MSG_CREDENCIALES);
        }
        if (!Boolean.TRUE.equals(cliente.getActivo())) {
            return ApiResponse.error(HttpStatus.FORBIDDEN, MSG_CLIENTE_INACTIVO);
        }

        intentosFallidos.reiniciar(cliente);
        boolean tieneA2F = Boolean.TRUE.equals(cliente.getA2fActivo());
        LoginResponse datos = new LoginResponse(
                tieneA2F ? PasoLogin.VERIFICAR_A2F : PasoLogin.CONFIGURAR_A2F,
                MetodoA2F.TOTP,
                tokenJwt.emitirA2F(cliente.getId(), cliente.getNombreUsuario()),
                tokenJwt.segundosA2F());
        String mensaje = tieneA2F
                ? "Contraseña correcta. Envíe el código de su app autenticadora."
                : "Contraseña correcta. Configure su segundo factor de autenticación.";
        log.info("Contrasena validada para cliente ID={}; siguiente paso {}", cliente.getId(), datos.getSiguientePaso());
        return ApiResponse.exito(HttpStatus.OK, mensaje, datos);
    }

    @Override
    @Transactional("sfTransactionManager")
    public ApiResponse<A2fConfiguracionResponse> configurarA2F(A2fConfiguracionRequest request) {
        Optional<Cliente> encontrado = clienteDelToken(request);
        if (encontrado.isEmpty()) {
            return ApiResponse.error(HttpStatus.UNAUTHORIZED, MSG_TOKEN_TEMPORAL);
        }
        Cliente cliente = encontrado.get();
        ApiResponse<A2fConfiguracionResponse> rechazo = validarEstado(cliente);
        if (rechazo != null) {
            return rechazo;
        }
        if (Boolean.TRUE.equals(cliente.getA2fActivo())) {
            return ApiResponse.error(HttpStatus.CONFLICT, MSG_A2F_YA_CONFIGURADO);
        }

        // Mientras no se confirme con un codigo, cada llamada genera un secreto nuevo
        String secreto = totp.generarSecreto();
        cliente.setA2fMetodo(MetodoA2F.TOTP);
        cliente.setA2fSecreto(secreto);
        cliente.setA2fUltimoPaso(null);
        clienteRepository.save(cliente);

        log.info("Secreto TOTP generado para cliente ID={}", cliente.getId());
        return ApiResponse.exito(HttpStatus.OK,
                "Registre el secreto en su app autenticadora y confirme con el primer código en /api/v1/auth/a2f/verificacion.",
                new A2fConfiguracionResponse(MetodoA2F.TOTP, secreto,
                        totp.uriOtpauth(emisorA2F, cliente.getNombreUsuario(), secreto)));
    }

    @Override
    @Transactional("sfTransactionManager")
    public ApiResponse<TokenResponse> verificarA2F(A2fVerificacionRequest request) {
        Optional<Cliente> encontrado = clienteDelToken(request);
        if (encontrado.isEmpty()) {
            return ApiResponse.error(HttpStatus.UNAUTHORIZED, MSG_TOKEN_TEMPORAL);
        }
        Cliente cliente = encontrado.get();
        ApiResponse<TokenResponse> rechazo = validarEstado(cliente);
        if (rechazo != null) {
            return rechazo;
        }
        if (cliente.getA2fSecreto() == null) {
            return ApiResponse.error(HttpStatus.CONFLICT, MSG_A2F_SIN_CONFIGURAR);
        }

        long paso = totp.verificar(cliente.getA2fSecreto(), request.getCodigo(), cliente.getA2fUltimoPaso());
        if (paso < 0) {
            return intentosFallidos.registrarFallo(cliente, MSG_CODIGO_INVALIDO);
        }

        boolean activacion = !Boolean.TRUE.equals(cliente.getA2fActivo());
        cliente.setA2fActivo(true);
        cliente.setA2fUltimoPaso(paso);
        intentosFallidos.reiniciar(cliente);

        String numeroCuenta = cliente.getCuenta() != null ? cliente.getCuenta().getNumeroCuenta() : null;
        TokenResponse datos = new TokenResponse(
                tokenJwt.emitirAcceso(cliente.getId(), cliente.getNombreUsuario()),
                "Bearer", tokenJwt.segundosAcceso(), cliente.getNombreUsuario(), numeroCuenta);
        log.info("Sesion iniciada para cliente ID={}{}", cliente.getId(), activacion ? " (A2F activado)" : "");
        return ApiResponse.exito(HttpStatus.OK,
                activacion ? "Segundo factor activado. Sesión iniciada correctamente." : "Sesión iniciada correctamente.",
                datos);
    }

    // ─── Metodos privados ────────────────────────────────────────────────────

    private Optional<Cliente> clienteDelToken(A2fTokenRequest request) {
        return tokenJwt.validarA2F(request.getTokenTemporal()).flatMap(clienteRepository::findConCuentaById);
    }

    /** El bloqueo y la baja logica tambien se revisan despues de la contrasena. Devuelve null si puede continuar. */
    private <T> ApiResponse<T> validarEstado(Cliente cliente) {
        if (intentosFallidos.estaBloqueado(cliente)) {
            return intentosFallidos.respuestaBloqueo(cliente);
        }
        if (!Boolean.TRUE.equals(cliente.getActivo())) {
            return ApiResponse.error(HttpStatus.FORBIDDEN, MSG_CLIENTE_INACTIVO);
        }
        return null;
    }
}
