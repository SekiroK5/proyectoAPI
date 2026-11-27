package com.proyecto.servicios.seguridad;

import com.proyecto.servicios.dto.response.ApiResponse;
import com.proyecto.servicios.entity.sf.Cliente;
import com.proyecto.servicios.enums.Catalogos.PropositoCodigo;
import com.proyecto.servicios.repository.sf.ClienteRepository;
import org.apache.commons.codec.binary.Base32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ValidadorA2F - autoriza pagos y depósitos con la app o con un código por correo")
class ValidadorA2FTest {

    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-11-26T18:00:00Z"), ZoneOffset.UTC);

    @Mock
    private ClienteRepository clienteRepository;
    @Mock
    private EnvioCorreo envioCorreo;

    private final Totp totp = new Totp(RELOJ);
    private VerificacionCorreo verificacionCorreo;
    private ValidadorA2F validador;
    private Cliente cliente;

    @BeforeEach
    void setUp() {
        verificacionCorreo = new VerificacionCorreo(envioCorreo, RELOJ);
        validador = new ValidadorA2F(totp, new IntentosFallidos(clienteRepository, RELOJ, 5, 15), clienteRepository, verificacionCorreo);
        cliente = new Cliente();
        cliente.setId(3);
        cliente.setNombre("Noe");
        cliente.setCorreoElectronico("noe@correo.com");
        cliente.setCorreoVerificado(true);
        lenient().when(envioCorreo.enviarCodigo(anyString(), anyString(), anyString(), anyLong(), any())).thenReturn(true);
    }

    private String codigoOperacion() {
        verificacionCorreo.emitir(cliente, PropositoCodigo.OPERACION);
        ArgumentCaptor<String> codigo = ArgumentCaptor.forClass(String.class);
        verify(envioCorreo).enviarCodigo(anyString(), anyString(), codigo.capture(), anyLong(), eq(PropositoCodigo.OPERACION));
        return codigo.getValue();
    }

    @Test
    @DisplayName("Sin app y sin código por correo: 409 pidiendo configurar o pedir código")
    void sinNingunFactor() {
        ApiResponse<Void> r = validador.validar(cliente, "123456");

        assertThat(r.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(r.getMensaje()).isEqualTo(ValidadorA2F.MSG_SIN_CONFIGURAR);
    }

    @Test
    @DisplayName("Código por correo correcto: autoriza y se consume (un solo uso)")
    void codigoCorreoCorrecto() {
        String codigo = codigoOperacion();
        when(clienteRepository.consumirCodigoCorreo(eq(3), anyString())).thenReturn(1);

        assertThat(validador.<Void>validar(cliente, codigo)).isNull();
        assertThat(cliente.getCorreoCodigoHash()).isNull();
    }

    @Test
    @DisplayName("Código por correo usado al mismo tiempo por otra petición: 409")
    void codigoCorreoEnUso() {
        String codigo = codigoOperacion();
        when(clienteRepository.consumirCodigoCorreo(eq(3), anyString())).thenReturn(0);

        ApiResponse<Void> r = validador.validar(cliente, codigo);

        assertThat(r.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(r.getMensaje()).isEqualTo(ValidadorA2F.MSG_EN_USO);
    }

    @Test
    @DisplayName("Solo código por correo y se escribe otro: cuenta como intento fallido (401)")
    void codigoCorreoIncorrecto() {
        codigoOperacion();

        ApiResponse<Void> r = validador.validar(cliente, "000000");

        assertThat(r.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(cliente.getIntentosFallidos()).isEqualTo(1);
    }

    @Test
    @DisplayName("Un código de inicio de sesión no sirve para autorizar operaciones")
    void codigoDeOtroProposito() {
        verificacionCorreo.emitir(cliente, PropositoCodigo.INICIO_SESION);

        assertThat(validador.<Void>validar(cliente, "123456").getStatus()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("Con app configurada y código por correo pendiente: el código de la app también sirve")
    void appYCorreo() {
        String secreto = totp.generarSecreto();
        cliente.setA2fSecreto(secreto);
        cliente.setA2fActivo(true);
        codigoOperacion();
        when(clienteRepository.marcarPasoA2F(anyInt(), anyLong())).thenReturn(1);

        String codigoApp = Totp.generarCodigo(new Base32().decode(secreto), RELOJ.millis() / 1000 / 30);

        assertThat(validador.<Void>validar(cliente, codigoApp)).isNull();
    }
}
