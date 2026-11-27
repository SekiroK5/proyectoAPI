package com.proyecto.servicios.service.impl;

import com.proyecto.servicios.dto.request.A2fConfiguracionRequest;
import com.proyecto.servicios.dto.request.A2fTokenRequest;
import com.proyecto.servicios.dto.request.A2fVerificacionRequest;
import com.proyecto.servicios.dto.request.CorreoReenvioRequest;
import com.proyecto.servicios.dto.request.CorreoVerificacionRequest;
import com.proyecto.servicios.dto.request.LoginRequest;
import com.proyecto.servicios.dto.response.A2fConfiguracionResponse;
import com.proyecto.servicios.dto.response.ApiResponse;
import com.proyecto.servicios.dto.response.LoginResponse;
import com.proyecto.servicios.dto.response.TokenResponse;
import com.proyecto.servicios.entity.sf.Cliente;
import com.proyecto.servicios.enums.Catalogos.MetodoA2F;
import com.proyecto.servicios.enums.Catalogos.PasoLogin;
import com.proyecto.servicios.enums.Catalogos.PropositoCodigo;
import com.proyecto.servicios.repository.sf.ClienteRepository;
import com.proyecto.servicios.seguridad.IntentosFallidos;
import com.proyecto.servicios.seguridad.TokenJwt;
import com.proyecto.servicios.seguridad.Totp;
import com.proyecto.servicios.seguridad.VerificacionCorreo;
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
    static final String MSG_CORREO_SIN_CONFIRMAR = "Primero confirme su correo con el código que le enviamos.";
    static final String MSG_CODIGO_CORREO_INVALIDO = "El código del correo no es correcto.";
    static final String MSG_CODIGO_CORREO_VENCIDO = "El código del correo venció. Pida uno nuevo.";
    static final String MSG_CODIGO_CORREO_SIN_INTENTOS = "Se agotaron los intentos con este código. Pida uno nuevo.";
    static final String MSG_CORREO_NO_ENVIADO = "No se pudo enviar el correo. Intente de nuevo en unos minutos.";

    private final ClienteRepository clienteRepository;
    private final VerificacionCorreo verificacionCorreo;
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
                           VerificacionCorreo verificacionCorreo,
                           @Value("${seguridad.a2f.emisor}") String emisorA2F) {
        this.clienteRepository = clienteRepository;
        this.verificacionCorreo = verificacionCorreo;
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
        if (!Boolean.TRUE.equals(cliente.getCorreoVerificado())) {
            log.info("Contrasena validada para cliente ID={}; falta confirmar el correo", cliente.getId());
            return ApiResponse.exito(HttpStatus.OK,
                    "Contraseña correcta. Confirme su correo con el código que le enviamos.",
                    pasoSiguiente(cliente, PasoLogin.VERIFICAR_CORREO));
        }
        return respuestaSegundoFactor(cliente, "Contraseña correcta.");
    }

    @Override
    @Transactional("sfTransactionManager")
    public ApiResponse<LoginResponse> verificarCorreo(CorreoVerificacionRequest request) {
        Optional<Cliente> encontrado = clienteDelToken(request);
        if (encontrado.isEmpty()) {
            return ApiResponse.error(HttpStatus.UNAUTHORIZED, MSG_TOKEN_TEMPORAL);
        }
        Cliente cliente = encontrado.get();
        ApiResponse<LoginResponse> rechazo = validarBloqueoYBaja(cliente);
        if (rechazo != null) {
            return rechazo;
        }
        if (Boolean.TRUE.equals(cliente.getCorreoVerificado())) {
            return respuestaSegundoFactor(cliente, "El correo ya estaba confirmado.");
        }

        VerificacionCorreo.Resultado resultado = verificacionCorreo.verificar(cliente, request.getCodigo(), PropositoCodigo.CONFIRMAR_CORREO);
        clienteRepository.save(cliente);
        return switch (resultado) {
            case CORRECTO -> {
                log.info("Correo confirmado para cliente ID={}", cliente.getId());
                yield respuestaSegundoFactor(cliente, "Correo confirmado.");
            }
            case INCORRECTO -> ApiResponse.error(HttpStatus.UNAUTHORIZED, MSG_CODIGO_CORREO_INVALIDO);
            case VENCIDO -> ApiResponse.error(HttpStatus.GONE, MSG_CODIGO_CORREO_VENCIDO);
            case SIN_INTENTOS -> ApiResponse.error(HttpStatus.TOO_MANY_REQUESTS, MSG_CODIGO_CORREO_SIN_INTENTOS);
        };
    }

    @Override
    @Transactional("sfTransactionManager")
    public ApiResponse<LoginResponse> reenviarCodigoCorreo(CorreoReenvioRequest request) {
        Optional<Cliente> encontrado = clienteDelToken(request);
        if (encontrado.isEmpty()) {
            return ApiResponse.error(HttpStatus.UNAUTHORIZED, MSG_TOKEN_TEMPORAL);
        }
        Cliente cliente = encontrado.get();
        ApiResponse<LoginResponse> rechazo = validarBloqueoYBaja(cliente);
        if (rechazo != null) {
            return rechazo;
        }
        if (Boolean.TRUE.equals(cliente.getCorreoVerificado())) {
            return ApiResponse.error(HttpStatus.CONFLICT, "El correo ya está confirmado. Continúe con su segundo factor.");
        }
        long espera = verificacionCorreo.segundosParaReenviar(cliente);
        if (espera > 0) {
            return ApiResponse.error(HttpStatus.TOO_MANY_REQUESTS,
                    "Espere " + espera + " segundos antes de pedir otro código.");
        }
        boolean enviado = verificacionCorreo.emitir(cliente, PropositoCodigo.CONFIRMAR_CORREO);
        clienteRepository.save(cliente);
        if (!enviado) {
            return ApiResponse.error(HttpStatus.SERVICE_UNAVAILABLE, MSG_CORREO_NO_ENVIADO);
        }
        return ApiResponse.exito(HttpStatus.OK, "Le enviamos un código nuevo a su correo.",
                pasoSiguiente(cliente, PasoLogin.VERIFICAR_CORREO));
    }

    @Override
    @Transactional("sfTransactionManager")
    public ApiResponse<LoginResponse> enviarCodigoInicioSesion(CorreoReenvioRequest request) {
        Optional<Cliente> encontrado = clienteDelToken(request);
        if (encontrado.isEmpty()) {
            return ApiResponse.error(HttpStatus.UNAUTHORIZED, MSG_TOKEN_TEMPORAL);
        }
        Cliente cliente = encontrado.get();
        ApiResponse<LoginResponse> rechazo = validarEstado(cliente);
        if (rechazo != null) {
            return rechazo;
        }
        ApiResponse<LoginResponse> limite = enviarCodigo(cliente, PropositoCodigo.INICIO_SESION);
        if (limite != null) {
            return limite;
        }
        log.info("Codigo de inicio de sesion enviado por correo al cliente ID={}", cliente.getId());
        return ApiResponse.exito(HttpStatus.OK, "Le enviamos un código a su correo para iniciar sesión.",
                pasoSiguiente(cliente, PasoLogin.VERIFICAR_CODIGO_CORREO));
    }

    @Override
    @Transactional("sfTransactionManager")
    public ApiResponse<TokenResponse> verificarCodigoInicioSesion(CorreoVerificacionRequest request) {
        Optional<Cliente> encontrado = clienteDelToken(request);
        if (encontrado.isEmpty()) {
            return ApiResponse.error(HttpStatus.UNAUTHORIZED, MSG_TOKEN_TEMPORAL);
        }
        Cliente cliente = encontrado.get();
        ApiResponse<TokenResponse> rechazo = validarEstado(cliente);
        if (rechazo != null) {
            return rechazo;
        }
        VerificacionCorreo.Resultado resultado =
                verificacionCorreo.verificar(cliente, request.getCodigo(), PropositoCodigo.INICIO_SESION);
        return switch (resultado) {
            case CORRECTO -> {
                intentosFallidos.reiniciar(cliente);
                clienteRepository.save(cliente);
                log.info("Sesion iniciada con codigo de correo para cliente ID={}", cliente.getId());
                yield ApiResponse.exito(HttpStatus.OK, "Sesión iniciada correctamente.", sesion(cliente));
            }
            // Un codigo incorrecto cuenta para el mismo bloqueo por intentos que la app autenticadora
            case INCORRECTO -> intentosFallidos.registrarFallo(cliente, MSG_CODIGO_CORREO_INVALIDO);
            case VENCIDO -> ApiResponse.error(HttpStatus.GONE, MSG_CODIGO_CORREO_VENCIDO);
            case SIN_INTENTOS -> {
                clienteRepository.save(cliente);
                yield ApiResponse.error(HttpStatus.TOO_MANY_REQUESTS, MSG_CODIGO_CORREO_SIN_INTENTOS);
            }
        };
    }

    @Override
    @Transactional("sfTransactionManager")
    public ApiResponse<Void> enviarCodigoOperacion(Integer clienteId) {
        Optional<Cliente> encontrado = clienteRepository.findConCuentaById(clienteId);
        if (encontrado.isEmpty()) {
            return ApiResponse.error(HttpStatus.UNAUTHORIZED, MSG_TOKEN_TEMPORAL);
        }
        Cliente cliente = encontrado.get();
        ApiResponse<Void> rechazo = validarEstado(cliente);
        if (rechazo != null) {
            return rechazo;
        }
        ApiResponse<Void> limite = enviarCodigo(cliente, PropositoCodigo.OPERACION);
        if (limite != null) {
            return limite;
        }
        log.info("Codigo para autorizar operacion enviado por correo al cliente ID={}", cliente.getId());
        return ApiResponse.exito(HttpStatus.OK,
                "Le enviamos un código a su correo. Úselo en lugar del código de la app para autorizar la operación.", null);
    }

    /** Respeta la espera entre envios y manda el codigo. Devuelve null si se envio; si no, el error. */
    private <T> ApiResponse<T> enviarCodigo(Cliente cliente, PropositoCodigo proposito) {
        long espera = verificacionCorreo.segundosParaReenviar(cliente);
        if (espera > 0) {
            return ApiResponse.error(HttpStatus.TOO_MANY_REQUESTS,
                    "Espere " + espera + " segundos antes de pedir otro código.");
        }
        boolean enviado = verificacionCorreo.emitir(cliente, proposito);
        clienteRepository.save(cliente);
        return enviado ? null : ApiResponse.error(HttpStatus.SERVICE_UNAVAILABLE, MSG_CORREO_NO_ENVIADO);
    }

    private TokenResponse sesion(Cliente cliente) {
        String numeroCuenta = cliente.getCuenta() != null ? cliente.getCuenta().getNumeroCuenta() : null;
        return new TokenResponse(
                tokenJwt.emitirAcceso(cliente.getId(), cliente.getNombreUsuario()),
                "Bearer", tokenJwt.segundosAcceso(), cliente.getNombreUsuario(), numeroCuenta);
    }

    private LoginResponse pasoSiguiente(Cliente cliente, PasoLogin paso) {
        return new LoginResponse(paso, MetodoA2F.TOTP,
                tokenJwt.emitirA2F(cliente.getId(), cliente.getNombreUsuario()), tokenJwt.segundosA2F());
    }

    private ApiResponse<LoginResponse> respuestaSegundoFactor(Cliente cliente, String prefijo) {
        boolean tieneA2F = Boolean.TRUE.equals(cliente.getA2fActivo());
        LoginResponse datos = pasoSiguiente(cliente, tieneA2F ? PasoLogin.VERIFICAR_A2F : PasoLogin.CONFIGURAR_A2F);
        String mensaje = prefijo + (tieneA2F
                ? " Envíe el código de su app autenticadora o pida uno por correo."
                : " Configure su app autenticadora o pida un código por correo.");
        log.info("Cliente ID={}; siguiente paso {}", cliente.getId(), datos.getSiguientePaso());
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

        TokenResponse datos = sesion(cliente);
        log.info("Sesion iniciada para cliente ID={}{}", cliente.getId(), activacion ? " (A2F activado)" : "");
        return ApiResponse.exito(HttpStatus.OK,
                activacion ? "Segundo factor activado. Sesión iniciada correctamente." : "Sesión iniciada correctamente.",
                datos);
    }

    // ─── Metodos privados ────────────────────────────────────────────────────

    private Optional<Cliente> clienteDelToken(A2fTokenRequest request) {
        return tokenJwt.validarA2F(request.getTokenTemporal()).flatMap(clienteRepository::findConCuentaById);
    }

    /**
     * Antes del segundo factor: bloqueo, baja logica y correo confirmado (el token temporal no basta
     * para saltarse la verificacion del correo). Devuelve null si puede continuar.
     */
    private <T> ApiResponse<T> validarEstado(Cliente cliente) {
        ApiResponse<T> rechazo = validarBloqueoYBaja(cliente);
        if (rechazo != null) {
            return rechazo;
        }
        if (!Boolean.TRUE.equals(cliente.getCorreoVerificado())) {
            return ApiResponse.error(HttpStatus.FORBIDDEN, MSG_CORREO_SIN_CONFIRMAR);
        }
        return null;
    }

    private <T> ApiResponse<T> validarBloqueoYBaja(Cliente cliente) {
        if (intentosFallidos.estaBloqueado(cliente)) {
            return intentosFallidos.respuestaBloqueo(cliente);
        }
        if (!Boolean.TRUE.equals(cliente.getActivo())) {
            return ApiResponse.error(HttpStatus.FORBIDDEN, MSG_CLIENTE_INACTIVO);
        }
        return null;
    }
}
