package com.proyecto.servicios.seguridad;

import com.proyecto.servicios.entity.sf.Cliente;
import com.proyecto.servicios.enums.Catalogos.PropositoCodigo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("VerificacionCorreo - códigos de 6 dígitos enviados al correo")
class VerificacionCorreoTest {

    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-11-26T18:00:00Z"), ZoneOffset.UTC);

    @Mock
    private EnvioCorreo envioCorreo;

    private VerificacionCorreo verificacion;
    private Cliente cliente;

    @BeforeEach
    void setUp() {
        verificacion = new VerificacionCorreo(envioCorreo, RELOJ);
        cliente = new Cliente();
        cliente.setNombre("Noe");
        cliente.setCorreoElectronico("noe@correo.com");
    }

    private String emitir(PropositoCodigo proposito) {
        when(envioCorreo.enviarCodigo(anyString(), anyString(), anyString(), anyLong(), any())).thenReturn(true);
        boolean enviado = proposito == PropositoCodigo.CONFIRMAR_CORREO ? verificacion.iniciar(cliente) : verificacion.emitir(cliente, proposito);
        assertThat(enviado).isTrue();
        ArgumentCaptor<String> codigo = ArgumentCaptor.forClass(String.class);
        verify(envioCorreo).enviarCodigo(eq("noe@correo.com"), eq("Noe"), codigo.capture(), eq(15L), eq(proposito));
        return codigo.getValue();
    }

    @Test
    @DisplayName("Al registrarse: correo sin confirmar, código de 6 dígitos y solo se guarda su hash")
    void iniciar_guardaHash() {
        String codigo = emitir(PropositoCodigo.CONFIRMAR_CORREO);

        assertThat(codigo).matches("\\d{6}");
        assertThat(cliente.getCorreoVerificado()).isFalse();
        assertThat(cliente.getCorreoCodigoHash()).hasSize(64).isNotEqualTo(codigo);
        assertThat(cliente.getCorreoCodigoProposito()).isEqualTo(PropositoCodigo.CONFIRMAR_CORREO);
        assertThat(cliente.getCorreoCodigoExpira()).isEqualTo(LocalDateTime.now(RELOJ).plusMinutes(15));
    }

    @Test
    @DisplayName("Código de confirmación correcto: confirma el correo y borra el código")
    void verificar_confirmacion() {
        String codigo = emitir(PropositoCodigo.CONFIRMAR_CORREO);

        assertThat(verificacion.verificar(cliente, codigo, PropositoCodigo.CONFIRMAR_CORREO)).isEqualTo(VerificacionCorreo.Resultado.CORRECTO);
        assertThat(cliente.getCorreoVerificado()).isTrue();
        assertThat(cliente.getCorreoCodigoHash()).isNull();
        assertThat(cliente.getCorreoCodigoProposito()).isNull();
    }

    @Test
    @DisplayName("Código de inicio de sesión correcto: no cambia la verificación del correo")
    void verificar_inicioSesion() {
        cliente.setCorreoVerificado(true);
        String codigo = emitir(PropositoCodigo.INICIO_SESION);

        assertThat(verificacion.verificar(cliente, codigo, PropositoCodigo.INICIO_SESION)).isEqualTo(VerificacionCorreo.Resultado.CORRECTO);
        assertThat(cliente.getCorreoVerificado()).isTrue();
        assertThat(cliente.getCorreoCodigoHash()).isNull();
    }

    @Test
    @DisplayName("Un código solo sirve para su propósito")
    void verificar_otroProposito() {
        String codigo = emitir(PropositoCodigo.INICIO_SESION);

        assertThat(verificacion.verificar(cliente, codigo, PropositoCodigo.CONFIRMAR_CORREO)).isEqualTo(VerificacionCorreo.Resultado.VENCIDO);
        assertThat(verificacion.tieneCodigoVigente(cliente, PropositoCodigo.OPERACION)).isFalse();
        assertThat(verificacion.tieneCodigoVigente(cliente, PropositoCodigo.INICIO_SESION)).isTrue();
    }

    @Test
    @DisplayName("Código nulo o incorrecto: suma intentos y no coincide")
    void verificar_incorrecto() {
        emitir(PropositoCodigo.CONFIRMAR_CORREO);

        assertThat(verificacion.verificar(cliente, null, PropositoCodigo.CONFIRMAR_CORREO)).isEqualTo(VerificacionCorreo.Resultado.INCORRECTO);
        assertThat(verificacion.verificar(cliente, "999999x", PropositoCodigo.CONFIRMAR_CORREO)).isEqualTo(VerificacionCorreo.Resultado.INCORRECTO);
        assertThat(cliente.getCorreoIntentos()).isEqualTo(2);
        assertThat(verificacion.coincide(cliente, null)).isFalse();
    }

    @Test
    @DisplayName("Sin código emitido o vencido: VENCIDO y sin código vigente")
    void verificar_sinCodigoOVencido() {
        assertThat(verificacion.verificar(cliente, "123456", PropositoCodigo.CONFIRMAR_CORREO)).isEqualTo(VerificacionCorreo.Resultado.VENCIDO);
        assertThat(verificacion.coincide(cliente, "123456")).isFalse();

        String codigo = emitir(PropositoCodigo.OPERACION);
        cliente.setCorreoCodigoExpira(LocalDateTime.now(RELOJ).minusSeconds(1));
        assertThat(verificacion.tieneCodigoVigente(cliente, PropositoCodigo.OPERACION)).isFalse();
        assertThat(verificacion.verificar(cliente, codigo, PropositoCodigo.OPERACION)).isEqualTo(VerificacionCorreo.Resultado.VENCIDO);
    }

    @Test
    @DisplayName("Intentos nulos se tratan como cero y al llegar al máximo devuelve SIN_INTENTOS")
    void verificar_intentosAgotados() {
        String codigo = emitir(PropositoCodigo.CONFIRMAR_CORREO);
        cliente.setCorreoIntentos(null);
        assertThat(verificacion.verificar(cliente, "000000", PropositoCodigo.CONFIRMAR_CORREO)).isEqualTo(VerificacionCorreo.Resultado.INCORRECTO);

        cliente.setCorreoIntentos(VerificacionCorreo.MAX_INTENTOS - 1);
        assertThat(verificacion.verificar(cliente, "000000", PropositoCodigo.CONFIRMAR_CORREO)).isEqualTo(VerificacionCorreo.Resultado.SIN_INTENTOS);
        assertThat(verificacion.tieneCodigoVigente(cliente, PropositoCodigo.CONFIRMAR_CORREO)).isFalse();
        assertThat(verificacion.verificar(cliente, codigo, PropositoCodigo.CONFIRMAR_CORREO)).isEqualTo(VerificacionCorreo.Resultado.SIN_INTENTOS);
    }

    @Test
    @DisplayName("Reenvío: 60 s de espera tras un envío y 0 si nunca se envió")
    void segundosParaReenviar() {
        assertThat(verificacion.segundosParaReenviar(cliente)).isZero();
        emitir(PropositoCodigo.CONFIRMAR_CORREO);
        assertThat(verificacion.segundosParaReenviar(cliente)).isEqualTo(60);
        cliente.setCorreoUltimoEnvio(LocalDateTime.now(RELOJ).minusSeconds(45));
        assertThat(verificacion.segundosParaReenviar(cliente)).isEqualTo(15);
        cliente.setCorreoUltimoEnvio(LocalDateTime.now(RELOJ).minusMinutes(5));
        assertThat(verificacion.segundosParaReenviar(cliente)).isZero();
    }
}
