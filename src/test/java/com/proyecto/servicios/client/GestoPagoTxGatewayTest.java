package com.proyecto.servicios.client;

import com.proyecto.servicios.client.GestoPagoTxGateway.Resultado;
import com.proyecto.servicios.seguridad.CifradoGestoPago;
import com.proyecto.servicios.service.GestoPagoTokenService;
import feign.FeignException;
import feign.Request;
import feign.RetryableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("GestoPagoTxGateway - cifrado, token y envio de sendTx")
class GestoPagoTxGatewayTest {

    private static final String PAYLOAD = "{\"idProducto\":582,\"idServicio\":133,\"telefono\":\"5555555551\"}";
    private static final String XML_EXITO = """
            <?xml version='1.0' encoding='UTF-8'?>
            <RESPONSE>
                <ID_TX>291437188</ID_TX>
                <NUM_AUTORIZACION>100024988</NUM_AUTORIZACION>
                <SALDO>157731.83</SALDO>
                <COMISION>0.7</COMISION>
                <FECHA>25/Feb/2022 18:15:25</FECHA>
                <MONTO>10.0</MONTO>
                <MENSAJE>
                    <CODIGO>01</CODIGO>
                    <TEXTO>Operacion realizada con exito</TEXTO>
                    <REFERENCIA></REFERENCIA>
                </MENSAJE>
            </RESPONSE>""";

    @Mock
    private GestoPagoTxClient txClient;

    @Mock
    private GestoPagoTokenService tokenService;

    private final CifradoGestoPago cifrado = new CifradoGestoPago(
            "HPo7OLqB4Fkk4E2yGOtwqw8H5fHR9kNx67OR4g4UdlA=", "p5ldmBPdd/9pjC0bDC/nSg==");

    private GestoPagoTxGateway gateway;

    @BeforeEach
    void setUp() throws Exception {
        gateway = new GestoPagoTxGateway(txClient, tokenService, cifrado,
                Clock.fixed(Instant.ofEpochMilli(1700000000000L), ZoneOffset.UTC), "");
    }

    private static FeignException error(int status) {
        Request req = Request.create(Request.HttpMethod.POST, "/sendTx.do", Map.of(), null, StandardCharsets.UTF_8, null);
        return FeignException.errorStatus("sendTx", feign.Response.builder().status(status).request(req)
                .headers(Map.of()).body("{\"token\":\"EXPIRED\"}", StandardCharsets.UTF_8).build());
    }

    @Test
    @DisplayName("Envía random + signed (payload cifrado) con el token Bearer y lee el XML de éxito")
    @SuppressWarnings("unchecked")
    void enviar_exito() {
        when(tokenService.obtenerTokenVigente()).thenReturn(Optional.of("tokenGP"));
        ArgumentCaptor<Map<String, ?>> formulario = ArgumentCaptor.forClass(Map.class);
        when(txClient.sendTx(eq("Bearer tokenGP"), isNull(), formulario.capture())).thenReturn(XML_EXITO);

        Resultado res = gateway.enviar(PAYLOAD);

        assertThat(res.tieneRespuesta()).isTrue();
        assertThat(res.codigo()).isEqualTo("01");
        assertThat(res.xml().idTransaccion()).isEqualTo("291437188");
        assertThat(res.xml().getNumAutorizacion()).isEqualTo("100024988");
        assertThat(formulario.getValue().get("random")).isEqualTo("1700000000000");
        assertThat(cifrado.descifrar((String) formulario.getValue().get("signed"))).isEqualTo(PAYLOAD);
    }

    @Test
    @DisplayName("Token rechazado (403): lo renueva y reintenta una sola vez")
    void enviar_tokenRechazado_reintenta() {
        when(tokenService.obtenerTokenVigente()).thenReturn(Optional.of("viejo"), Optional.of("nuevo"));
        when(txClient.sendTx(eq("Bearer viejo"), isNull(), anyMap())).thenThrow(error(403));
        when(txClient.sendTx(eq("Bearer nuevo"), isNull(), anyMap())).thenReturn(XML_EXITO);

        assertThat(gateway.enviar(PAYLOAD).codigo()).isEqualTo("01");
        verify(tokenService).renovarToken();
    }

    @Test
    @DisplayName("Timeout: resultado desconocido (enviada, sin respuesta) para confirmarlo después")
    void enviar_timeout() {
        when(tokenService.obtenerTokenVigente()).thenReturn(Optional.of("tokenGP"));
        when(txClient.sendTx(any(), any(), anyMap())).thenThrow(new RetryableException(
                -1, "Read timed out", Request.HttpMethod.POST, (Long) null,
                Request.create(Request.HttpMethod.POST, "/sendTx.do", Map.of(), null, StandardCharsets.UTF_8, null)));

        Resultado res = gateway.enviar(PAYLOAD);

        assertThat(res.tieneRespuesta()).isFalse();
        assertThat(res.enviada()).isTrue();
    }

    @Test
    @DisplayName("Sin token no envía nada y lo indica (enviada = false)")
    void enviar_sinToken() {
        when(tokenService.obtenerTokenVigente()).thenReturn(Optional.empty());

        Resultado res = gateway.enviar(PAYLOAD);

        assertThat(res.enviada()).isFalse();
        verify(txClient, never()).sendTx(any(), any(), anyMap());
    }

    @Test
    @DisplayName("Sin llave/IV de cifrado no envía nada (enviada = false)")
    void enviar_sinCifrado() throws Exception {
        GestoPagoTxGateway sinLlaves = new GestoPagoTxGateway(txClient, tokenService, new CifradoGestoPago("", ""),
                Clock.systemUTC(), "");

        Resultado res = sinLlaves.enviar(PAYLOAD);

        assertThat(res.enviada()).isFalse();
        verify(txClient, never()).sendTx(any(), any(), anyMap());
    }

    @Test
    @DisplayName("Una respuesta que no es XML válido se trata como resultado desconocido")
    void enviar_respuestaIlegible() {
        when(tokenService.obtenerTokenVigente()).thenReturn(Optional.of("tokenGP"));
        when(txClient.sendTx(any(), any(), anyMap())).thenReturn("<html>error</html>");

        Resultado res = gateway.enviar(PAYLOAD);

        assertThat(res.tieneRespuesta()).isFalse();
        assertThat(res.enviada()).isTrue();
        assertThat(res.cuerpo()).isEqualTo("<html>error</html>");
    }

    @Test
    @DisplayName("confirmTx lee el ID_TX que viene dentro de MENSAJE")
    void confirmar_leeIdTxDeMensaje() {
        when(tokenService.obtenerTokenVigente()).thenReturn(Optional.of("tokenGP"));
        when(txClient.confirmTx(any(), any(), anyMap())).thenReturn("""
                <RESPONSE><NUM_AUTORIZACION>100024988</NUM_AUTORIZACION>
                <MENSAJE><CODIGO>06</CODIGO><TEXTO>La transaccion fue aplicada exitosamente.</TEXTO>
                <ID_TX>291437188</ID_TX></MENSAJE></RESPONSE>""");

        Resultado res = gateway.confirmar(PAYLOAD);

        assertThat(res.codigo()).isEqualTo("06");
        assertThat(res.xml().idTransaccion()).isEqualTo("291437188");
    }

    @Test
    @DisplayName("Lee el formato <AUTORIZACION> (código 03, carrier sin respuesta) como respuesta con código")
    void enviar_formatoAutorizacion() {
        when(tokenService.obtenerTokenVigente()).thenReturn(Optional.of("tokenGP"));
        when(txClient.sendTx(any(), any(), anyMap())).thenReturn("""
                <?xml version='1.0' encoding='UTF-8'?>
                <AUTORIZACION>
                    <NUM_AUTORIZACION>-1</NUM_AUTORIZACION>
                    <CODIGO_MENSAJE>03</CODIGO_MENSAJE>
                    <MENSAJE>Por el momento el carrier no responde, favor de intentar mas tarde.</MENSAJE>
                </AUTORIZACION>""");

        Resultado res = gateway.enviar(PAYLOAD);

        assertThat(res.tieneRespuesta()).isTrue();
        assertThat(res.codigo()).isEqualTo("03");
        assertThat(res.xml().getNumAutorizacion()).isEqualTo("-1");
        assertThat(res.xml().texto()).startsWith("Por el momento el carrier no responde");
    }

    @Test
    @DisplayName("Un XML válido pero sin código se trata como resultado desconocido")
    void enviar_xmlSinCodigo() {
        when(tokenService.obtenerTokenVigente()).thenReturn(Optional.of("tokenGP"));
        when(txClient.sendTx(any(), any(), anyMap())).thenReturn("<RESPONSE><NUM_AUTORIZACION>5</NUM_AUTORIZACION></RESPONSE>");

        Resultado res = gateway.enviar(PAYLOAD);

        assertThat(res.tieneRespuesta()).isFalse();
        assertThat(res.enviada()).isTrue();
    }
}
