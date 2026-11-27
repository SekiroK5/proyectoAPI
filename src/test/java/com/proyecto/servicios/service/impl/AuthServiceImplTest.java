package com.proyecto.servicios.service.impl;

import com.proyecto.servicios.dto.request.A2fConfiguracionRequest;
import com.proyecto.servicios.dto.request.A2fVerificacionRequest;
import com.proyecto.servicios.dto.request.CorreoReenvioRequest;
import com.proyecto.servicios.dto.request.CorreoVerificacionRequest;
import com.proyecto.servicios.dto.request.LoginRequest;
import com.proyecto.servicios.dto.response.A2fConfiguracionResponse;
import com.proyecto.servicios.dto.response.ApiResponse;
import com.proyecto.servicios.dto.response.LoginResponse;
import com.proyecto.servicios.dto.response.TokenResponse;
import com.proyecto.servicios.entity.sf.Cliente;
import com.proyecto.servicios.entity.sf.Cuenta;
import com.proyecto.servicios.enums.Catalogos.MetodoA2F;
import com.proyecto.servicios.enums.Catalogos.PasoLogin;
import com.proyecto.servicios.enums.Catalogos.PropositoCodigo;
import com.proyecto.servicios.repository.sf.ClienteRepository;
import com.proyecto.servicios.seguridad.EnvioCorreo;
import com.proyecto.servicios.seguridad.IntentosFallidos;
import com.proyecto.servicios.seguridad.TokenJwt;
import com.proyecto.servicios.seguridad.Totp;
import com.proyecto.servicios.seguridad.VerificacionCorreo;
import org.apache.commons.codec.binary.Base32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthServiceImpl - inicio de sesion con contraseña y A2F TOTP")
class AuthServiceImplTest {

    private static final String USUARIO = "juanperez95";
    private static final String CONTRASENA = "Segura#2026";
    private static final int MAX_INTENTOS = 3;
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-10-23T18:00:00Z"), ZoneOffset.UTC);

    @Mock
    private ClienteRepository clienteRepository;

    @Mock
    private EnvioCorreo envioCorreo;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private final TokenJwt tokenJwt = new TokenJwt("secreto-de-pruebas-de-al-menos-32-bytes!", "productosList", 30, 5, Clock.systemUTC());
    private final Totp totp = new Totp(RELOJ);

    private AuthServiceImpl authService;
    private Cliente cliente;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(clienteRepository, passwordEncoder, tokenJwt, totp,
                new IntentosFallidos(clienteRepository, RELOJ, MAX_INTENTOS, 15),
                new VerificacionCorreo(envioCorreo, RELOJ), "productosList");

        cliente = new Cliente();
        cliente.setId(1);
        cliente.setNombreUsuario(USUARIO);
        cliente.setCorreoElectronico("juan.perez@correo.com");
        cliente.setActivo(true);
        cliente.setContrasenaHash(passwordEncoder.encode(CONTRASENA));
        Cuenta cuenta = new Cuenta();
        cuenta.setNumeroCuenta("1234567890123456");
        cliente.setCuenta(cuenta);

        lenient().when(clienteRepository.findByNombreUsuarioOrCorreoElectronico(anyString(), anyString()))
                .thenReturn(Optional.empty());
        lenient().when(clienteRepository.findByNombreUsuarioOrCorreoElectronico(USUARIO, USUARIO))
                .thenReturn(Optional.of(cliente));
        lenient().when(clienteRepository.findConCuentaById(anyInt())).thenReturn(Optional.empty());
        lenient().when(clienteRepository.findConCuentaById(1)).thenReturn(Optional.of(cliente));
    }

    // ======================== Datos de prueba ========================

    private static LoginRequest login(String usuario, String contrasena) {
        LoginRequest req = new LoginRequest();
        req.setUsuario(usuario);
        req.setContrasena(contrasena);
        return req;
    }

    private static A2fConfiguracionRequest configuracion(String token) {
        A2fConfiguracionRequest req = new A2fConfiguracionRequest();
        req.setTokenTemporal(token);
        return req;
    }

    private static A2fVerificacionRequest verificacion(String token, String codigo) {
        A2fVerificacionRequest req = new A2fVerificacionRequest();
        req.setTokenTemporal(token);
        req.setCodigo(codigo);
        return req;
    }

    private String tokenTemporal() {
        return authService.iniciarSesion(login(USUARIO, CONTRASENA)).getDatos().getTokenTemporal();
    }

    private void conA2fActivo() {
        cliente.setA2fMetodo(MetodoA2F.TOTP);
        cliente.setA2fSecreto(totp.generarSecreto());
        cliente.setA2fActivo(true);
    }

    private String codigoActual() {
        long paso = RELOJ.millis() / 1000 / 30;
        return Totp.generarCodigo(new Base32().decode(cliente.getA2fSecreto()), paso);
    }

    private static void esError(ApiResponse<?> res, HttpStatus status, String mensaje) {
        assertThat(res.getCodigo()).isEqualTo(ApiResponse.CODIGO_ERROR);
        assertThat(res.getStatus()).isEqualTo(status);
        assertThat(res.getMensaje()).isEqualTo(mensaje);
        assertThat(res.getDatos()).isNull();
    }

    // ======================== Paso 1: contraseña ========================

    @Test
    @DisplayName("Contraseña correcta sin A2F: pide configurarlo y entrega token temporal")
    void login_sinA2F_pideConfigurar() {
        ApiResponse<LoginResponse> res = authService.iniciarSesion(login(USUARIO, CONTRASENA));

        assertThat(res.getCodigo()).isEqualTo(ApiResponse.CODIGO_EXITO);
        assertThat(res.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(res.getDatos().getSiguientePaso()).isEqualTo(PasoLogin.CONFIGURAR_A2F);
        assertThat(res.getDatos().getExpiraEnSegundos()).isEqualTo(300);
        assertThat(tokenJwt.validarA2F(res.getDatos().getTokenTemporal())).contains(1);
    }

    @Test
    @DisplayName("Contraseña correcta con A2F activo: pide el código")
    void login_conA2F_pideCodigo() {
        conA2fActivo();

        assertThat(authService.iniciarSesion(login(USUARIO, CONTRASENA)).getDatos().getSiguientePaso())
                .isEqualTo(PasoLogin.VERIFICAR_A2F);
    }

    @Test
    @DisplayName("Acepta el correo electrónico en lugar del usuario")
    void login_conCorreo() {
        when(clienteRepository.findByNombreUsuarioOrCorreoElectronico("juan.perez@correo.com", "juan.perez@correo.com"))
                .thenReturn(Optional.of(cliente));

        assertThat(authService.iniciarSesion(login("Juan.Perez@Correo.com", CONTRASENA)).getCodigo())
                .isEqualTo(ApiResponse.CODIGO_EXITO);
    }

    @Test
    @DisplayName("Usuario inexistente y contraseña incorrecta responden el mismo mensaje (401)")
    void login_credencialesIncorrectas() {
        esError(authService.iniciarSesion(login("nadie", CONTRASENA)), HttpStatus.UNAUTHORIZED, AuthServiceImpl.MSG_CREDENCIALES);
        esError(authService.iniciarSesion(login(USUARIO, "Otra#2026")), HttpStatus.UNAUTHORIZED, AuthServiceImpl.MSG_CREDENCIALES);
        assertThat(cliente.getIntentosFallidos()).isEqualTo(1);
    }

    @Test
    @DisplayName("Cliente registrado antes de V6 (sin contraseña) no puede iniciar sesión")
    void login_sinContrasena() {
        cliente.setContrasenaHash(null);

        esError(authService.iniciarSesion(login(USUARIO, CONTRASENA)), HttpStatus.UNAUTHORIZED, AuthServiceImpl.MSG_CREDENCIALES);
    }

    @Test
    @DisplayName("Bloquea el inicio de sesión al llegar al máximo de intentos (423), aun con la contraseña correcta")
    void login_bloqueoPorIntentos() {
        authService.iniciarSesion(login(USUARIO, "Mala#1"));
        authService.iniciarSesion(login(USUARIO, "Mala#2"));
        ApiResponse<LoginResponse> res = authService.iniciarSesion(login(USUARIO, "Mala#3"));

        assertThat(res.getStatus()).isEqualTo(HttpStatus.LOCKED);
        assertThat(res.getMensaje()).contains("18:15");
        assertThat(cliente.getBloqueadoHasta()).isEqualTo(LocalDateTime.now(RELOJ).plusMinutes(15));
        assertThat(authService.iniciarSesion(login(USUARIO, CONTRASENA)).getStatus()).isEqualTo(HttpStatus.LOCKED);
    }

    @Test
    @DisplayName("El bloqueo vencido ya no impide iniciar sesión y reinicia los intentos")
    void login_bloqueoVencido() {
        cliente.setBloqueadoHasta(LocalDateTime.now(RELOJ).minusMinutes(1));
        cliente.setIntentosFallidos(2);

        assertThat(authService.iniciarSesion(login(USUARIO, CONTRASENA)).getCodigo()).isEqualTo(ApiResponse.CODIGO_EXITO);
        assertThat(cliente.getIntentosFallidos()).isZero();
        assertThat(cliente.getBloqueadoHasta()).isNull();
    }

    @Test
    @DisplayName("Cliente dado de baja: 403 sin token")
    void login_clienteInactivo() {
        cliente.setActivo(false);

        esError(authService.iniciarSesion(login(USUARIO, CONTRASENA)), HttpStatus.FORBIDDEN, AuthServiceImpl.MSG_CLIENTE_INACTIVO);
    }

    // ======================== Configuración A2F ========================

    @Test
    @DisplayName("Configurar A2F genera el secreto TOTP y la URI otpauth sin activarlo todavía")
    void configurar_generaSecreto() {
        ApiResponse<A2fConfiguracionResponse> res = authService.configurarA2F(configuracion(tokenTemporal()));

        assertThat(res.getCodigo()).isEqualTo(ApiResponse.CODIGO_EXITO);
        assertThat(res.getDatos().getSecreto()).isEqualTo(cliente.getA2fSecreto()).hasSize(32);
        assertThat(res.getDatos().getUriOtpauth()).startsWith("otpauth://totp/productosList:juanperez95?secret=");
        assertThat(cliente.getA2fMetodo()).isEqualTo(MetodoA2F.TOTP);
        assertThat(cliente.getA2fActivo()).isFalse();
    }

    @Test
    @DisplayName("No permite reconfigurar un A2F ya activo (409)")
    void configurar_yaActivo() {
        conA2fActivo();

        esError(authService.configurarA2F(configuracion(tokenTemporal())), HttpStatus.CONFLICT, AuthServiceImpl.MSG_A2F_YA_CONFIGURADO);
    }

    @Test
    @DisplayName("Rechaza un token temporal inválido o un token de acceso (401)")
    void configurar_tokenInvalido() {
        esError(authService.configurarA2F(configuracion("no-es-un-jwt")), HttpStatus.UNAUTHORIZED, AuthServiceImpl.MSG_TOKEN_TEMPORAL);
        esError(authService.configurarA2F(configuracion(tokenJwt.emitirAcceso(1, USUARIO))),
                HttpStatus.UNAUTHORIZED, AuthServiceImpl.MSG_TOKEN_TEMPORAL);
    }

    // ======================== Paso 2: código A2F ========================

    @Test
    @DisplayName("Primer código correcto: activa el A2F y entrega token de acceso con el número de cuenta")
    void verificar_activaYEntregaToken() {
        String token = tokenTemporal();
        authService.configurarA2F(configuracion(token));

        ApiResponse<TokenResponse> res = authService.verificarA2F(verificacion(token, codigoActual()));

        assertThat(res.getCodigo()).isEqualTo(ApiResponse.CODIGO_EXITO);
        assertThat(res.getMensaje()).contains("activado");
        assertThat(cliente.getA2fActivo()).isTrue();
        assertThat(res.getDatos().getTipoToken()).isEqualTo("Bearer");
        assertThat(res.getDatos().getNumeroCuenta()).isEqualTo("1234567890123456");
        assertThat(tokenJwt.decoderAcceso().decode(res.getDatos().getTokenAcceso()).getSubject()).isEqualTo("1");
    }

    @Test
    @DisplayName("Con A2F activo, el código correcto inicia sesión")
    void verificar_conA2fActivo() {
        conA2fActivo();

        ApiResponse<TokenResponse> res = authService.verificarA2F(verificacion(tokenTemporal(), codigoActual()));

        assertThat(res.getCodigo()).isEqualTo(ApiResponse.CODIGO_EXITO);
        assertThat(res.getMensaje()).isEqualTo("Sesión iniciada correctamente.");
    }

    @Test
    @DisplayName("El mismo código no se puede usar dos veces")
    void verificar_codigoReutilizado() {
        conA2fActivo();
        String token = tokenTemporal();
        String codigo = codigoActual();
        authService.verificarA2F(verificacion(token, codigo));

        esError(authService.verificarA2F(verificacion(token, codigo)), HttpStatus.UNAUTHORIZED, AuthServiceImpl.MSG_CODIGO_INVALIDO);
    }

    @Test
    @DisplayName("Los códigos incorrectos cuentan como intentos fallidos y terminan en bloqueo")
    void verificar_codigoIncorrecto_bloquea() {
        conA2fActivo();
        String token = tokenTemporal();
        String malo = codigoActual().equals("000000") ? "111111" : "000000";

        esError(authService.verificarA2F(verificacion(token, malo)), HttpStatus.UNAUTHORIZED, AuthServiceImpl.MSG_CODIGO_INVALIDO);
        authService.verificarA2F(verificacion(token, malo));
        assertThat(authService.verificarA2F(verificacion(token, malo)).getStatus()).isEqualTo(HttpStatus.LOCKED);
        assertThat(authService.verificarA2F(verificacion(token, codigoActual())).getStatus()).isEqualTo(HttpStatus.LOCKED);
    }

    @Test
    @DisplayName("Verificar sin haber configurado el A2F responde 409")
    void verificar_sinConfigurar() {
        esError(authService.verificarA2F(verificacion(tokenTemporal(), "123456")), HttpStatus.CONFLICT, AuthServiceImpl.MSG_A2F_SIN_CONFIGURAR);
        assertThat(cliente.getIntentosFallidos()).isZero();
    }

    // ======================== Verificacion del correo ========================

    private static CorreoVerificacionRequest codigoCorreo(String token, String codigo) {
        CorreoVerificacionRequest req = new CorreoVerificacionRequest();
        req.setTokenTemporal(token);
        req.setCodigo(codigo);
        return req;
    }

    private static CorreoReenvioRequest reenvio(String token) {
        CorreoReenvioRequest req = new CorreoReenvioRequest();
        req.setTokenTemporal(token);
        return req;
    }

    /** Deja al cliente con el correo sin confirmar y devuelve el codigo que se "envio". */
    private String conCorreoPendiente() {
        when(envioCorreo.enviarCodigo(anyString(), any(), anyString(), anyLong(), any())).thenReturn(true);
        new VerificacionCorreo(envioCorreo, RELOJ).iniciar(cliente);
        ArgumentCaptor<String> codigo = ArgumentCaptor.forClass(String.class);
        verify(envioCorreo).enviarCodigo(eq("juan.perez@correo.com"), any(), codigo.capture(), eq(15L), any());
        return codigo.getValue();
    }

    @Test
    @DisplayName("Login con correo sin confirmar: el siguiente paso es VERIFICAR_CORREO")
    void login_correoSinConfirmar() {
        conCorreoPendiente();

        ApiResponse<LoginResponse> r = authService.iniciarSesion(login(USUARIO, CONTRASENA));

        assertThat(r.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(r.getDatos().getSiguientePaso()).isEqualTo(PasoLogin.VERIFICAR_CORREO);
        assertThat(r.getDatos().getTokenTemporal()).isNotBlank();
    }

    @Test
    @DisplayName("Con el correo sin confirmar no se puede saltar al segundo factor")
    void a2f_bloqueadoSinCorreo() {
        conCorreoPendiente();
        String token = tokenTemporal();

        assertThat(authService.configurarA2F(configuracion(token)).getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(authService.verificarA2F(verificacion(token, "123456")).getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("Código correcto: confirma el correo y continúa con CONFIGURAR_A2F")
    void verificarCorreo_correcto() {
        String codigo = conCorreoPendiente();

        ApiResponse<LoginResponse> r = authService.verificarCorreo(codigoCorreo(tokenTemporal(), codigo));

        assertThat(r.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(r.getDatos().getSiguientePaso()).isEqualTo(PasoLogin.CONFIGURAR_A2F);
        assertThat(cliente.getCorreoVerificado()).isTrue();
        assertThat(cliente.getCorreoCodigoHash()).isNull();
    }

    @Test
    @DisplayName("Código incorrecto: 401 y cuenta el intento; al quinto se agotan")
    void verificarCorreo_incorrecto() {
        conCorreoPendiente();
        String token = tokenTemporal();

        for (int i = 1; i < VerificacionCorreo.MAX_INTENTOS; i++) {
            assertThat(authService.verificarCorreo(codigoCorreo(token, "000000")).getStatus())
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
        }
        assertThat(authService.verificarCorreo(codigoCorreo(token, "000000")).getStatus())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(authService.verificarCorreo(codigoCorreo(token, "000000")).getStatus())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(cliente.getCorreoVerificado()).isFalse();
    }

    @Test
    @DisplayName("Código vencido: 410")
    void verificarCorreo_vencido() {
        String codigo = conCorreoPendiente();
        cliente.setCorreoCodigoExpira(LocalDateTime.now(RELOJ).minusSeconds(1));

        assertThat(authService.verificarCorreo(codigoCorreo(tokenTemporal(), codigo)).getStatus())
                .isEqualTo(HttpStatus.GONE);
    }

    @Test
    @DisplayName("Correo ya confirmado: continúa al segundo factor sin pedir código")
    void verificarCorreo_yaConfirmado() {
        ApiResponse<LoginResponse> r = authService.verificarCorreo(codigoCorreo(tokenTemporal(), "123456"));

        assertThat(r.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(r.getDatos().getSiguientePaso()).isEqualTo(PasoLogin.CONFIGURAR_A2F);
    }

    @Test
    @DisplayName("Token temporal inválido: 401 en verificar y reenviar")
    void correo_tokenInvalido() {
        assertThat(authService.verificarCorreo(codigoCorreo("no-es-token", "123456")).getStatus())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(authService.reenviarCodigoCorreo(reenvio("no-es-token")).getStatus())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Reenvío: se limita a uno por minuto")
    void reenviar_limitado() {
        conCorreoPendiente();

        ApiResponse<LoginResponse> r = authService.reenviarCodigoCorreo(reenvio(tokenTemporal()));

        assertThat(r.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(r.getMensaje()).contains("60 segundos");
    }

    @Test
    @DisplayName("Reenvío después de un minuto: genera un código nuevo")
    void reenviar_correcto() {
        conCorreoPendiente();
        cliente.setCorreoUltimoEnvio(LocalDateTime.now(RELOJ).minusMinutes(2));

        ApiResponse<LoginResponse> r = authService.reenviarCodigoCorreo(reenvio(tokenTemporal()));

        assertThat(r.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(r.getDatos().getSiguientePaso()).isEqualTo(PasoLogin.VERIFICAR_CORREO);
    }

    @Test
    @DisplayName("Reenvío con fallo del SMTP: 503")
    void reenviar_smtpFalla() {
        conCorreoPendiente();
        cliente.setCorreoUltimoEnvio(LocalDateTime.now(RELOJ).minusMinutes(2));
        when(envioCorreo.enviarCodigo(anyString(), any(), anyString(), anyLong(), any())).thenReturn(false);

        assertThat(authService.reenviarCodigoCorreo(reenvio(tokenTemporal())).getStatus())
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    @DisplayName("Reenvío con correo ya confirmado: 409 y no envía nada")
    void reenviar_yaConfirmado() {
        assertThat(authService.reenviarCodigoCorreo(reenvio(tokenTemporal())).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
        verify(envioCorreo, never()).enviarCodigo(anyString(), any(), anyString(), anyLong(), any());
    }

    @Test
    @DisplayName("Cliente dado de baja no puede confirmar su correo")
    void verificarCorreo_clienteInactivo() {
        String codigo = conCorreoPendiente();
        String token = tokenTemporal();
        cliente.setActivo(false);

        assertThat(authService.verificarCorreo(codigoCorreo(token, codigo)).getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(authService.reenviarCodigoCorreo(reenvio(token)).getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ======================== Segundo factor por correo ========================

    private String codigoInicioSesionEnviado(String token) {
        when(envioCorreo.enviarCodigo(anyString(), any(), anyString(), anyLong(), any())).thenReturn(true);
        ApiResponse<LoginResponse> r = authService.enviarCodigoInicioSesion(reenvio(token));
        assertThat(r.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(r.getDatos().getSiguientePaso()).isEqualTo(PasoLogin.VERIFICAR_CODIGO_CORREO);
        ArgumentCaptor<String> codigo = ArgumentCaptor.forClass(String.class);
        verify(envioCorreo).enviarCodigo(eq("juan.perez@correo.com"), any(), codigo.capture(), eq(15L),
                eq(PropositoCodigo.INICIO_SESION));
        return codigo.getValue();
    }

    @Test
    @DisplayName("Inicio de sesión con código por correo (sin app autenticadora): entrega el token de acceso")
    void inicioSesionPorCorreo_correcto() {
        String token = tokenTemporal();
        String codigo = codigoInicioSesionEnviado(token);

        ApiResponse<TokenResponse> r = authService.verificarCodigoInicioSesion(codigoCorreo(token, codigo));

        assertThat(r.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(r.getDatos().getTokenAcceso()).isNotBlank();
        assertThat(r.getDatos().getNumeroCuenta()).isEqualTo("1234567890123456");
        assertThat(cliente.getCorreoCodigoHash()).isNull();
    }

    @Test
    @DisplayName("Código de inicio de sesión incorrecto: cuenta para el bloqueo por intentos")
    void inicioSesionPorCorreo_incorrecto() {
        String token = tokenTemporal();
        codigoInicioSesionEnviado(token);

        ApiResponse<TokenResponse> r = authService.verificarCodigoInicioSesion(codigoCorreo(token, "000000"));

        assertThat(r.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(cliente.getIntentosFallidos()).isEqualTo(1);
    }

    @Test
    @DisplayName("Código de inicio de sesión vencido: 410; sin intentos: 429")
    void inicioSesionPorCorreo_vencidoYSinIntentos() {
        String token = tokenTemporal();
        String codigo = codigoInicioSesionEnviado(token);
        cliente.setCorreoIntentos(VerificacionCorreo.MAX_INTENTOS);
        assertThat(authService.verificarCodigoInicioSesion(codigoCorreo(token, codigo)).getStatus())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);

        cliente.setCorreoIntentos(0);
        cliente.setCorreoCodigoExpira(LocalDateTime.now(RELOJ).minusSeconds(1));
        assertThat(authService.verificarCodigoInicioSesion(codigoCorreo(token, codigo)).getStatus())
                .isEqualTo(HttpStatus.GONE);
    }

    @Test
    @DisplayName("El código de confirmar correo no sirve para iniciar sesión")
    void inicioSesionPorCorreo_codigoDeConfirmacion() {
        String codigo = conCorreoPendiente();
        cliente.setCorreoVerificado(true);

        assertThat(authService.verificarCodigoInicioSesion(codigoCorreo(tokenTemporal(), codigo)).getStatus())
                .isEqualTo(HttpStatus.GONE);
    }

    @Test
    @DisplayName("Inicio de sesión por correo: token inválido 401, correo sin confirmar 403, espera entre envíos 429")
    void inicioSesionPorCorreo_rechazos() {
        assertThat(authService.enviarCodigoInicioSesion(reenvio("no-es-token")).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(authService.verificarCodigoInicioSesion(codigoCorreo("no-es-token", "123456")).getStatus())
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        String token = tokenTemporal();
        codigoInicioSesionEnviado(token);
        assertThat(authService.enviarCodigoInicioSesion(reenvio(token)).getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);

        cliente.setCorreoVerificado(false);
        assertThat(authService.enviarCodigoInicioSesion(reenvio(token)).getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(authService.verificarCodigoInicioSesion(codigoCorreo(token, "123456")).getStatus())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("Código para autorizar una operación: se envía al correo del cliente de la sesión")
    void codigoOperacion_enviado() {
        when(envioCorreo.enviarCodigo(anyString(), any(), anyString(), anyLong(), any())).thenReturn(true);

        ApiResponse<Void> r = authService.enviarCodigoOperacion(1);

        assertThat(r.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(cliente.getCorreoCodigoProposito()).isEqualTo(PropositoCodigo.OPERACION);
        verify(envioCorreo).enviarCodigo(eq("juan.perez@correo.com"), any(), anyString(), eq(15L), eq(PropositoCodigo.OPERACION));
    }

    @Test
    @DisplayName("Código para operación: cliente inexistente 401, SMTP caído 503, dado de baja 403")
    void codigoOperacion_rechazos() {
        assertThat(authService.enviarCodigoOperacion(99).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);

        when(envioCorreo.enviarCodigo(anyString(), any(), anyString(), anyLong(), any())).thenReturn(false);
        assertThat(authService.enviarCodigoOperacion(1).getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);

        cliente.setCorreoUltimoEnvio(null);
        cliente.setActivo(false);
        assertThat(authService.enviarCodigoOperacion(1).getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
