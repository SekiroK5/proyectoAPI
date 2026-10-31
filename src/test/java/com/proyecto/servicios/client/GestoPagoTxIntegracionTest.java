package com.proyecto.servicios.client;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.proyecto.servicios.client.GestoPagoTxGateway.Resultado;
import com.proyecto.servicios.seguridad.CifradoGestoPago;
import com.proyecto.servicios.service.GestoPagoTokenService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.openfeign.FeignAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Integracion real de la capa HTTP: cliente Feign + cifrado + gateway contra un GestoPago simulado con
 * WireMock. Comprueba lo que viaja por la red (formulario urlencoded, encabezados, payload cifrado),
 * la lectura del XML, el reintento con token renovado y el timeout de lectura.
 */
@SpringBootTest(classes = GestoPagoTxIntegracionTest.Contexto.class, properties = {
        "gestopago.cifrado.llave=HPo7OLqB4Fkk4E2yGOtwqw8H5fHR9kNx67OR4g4UdlA=",
        "gestopago.cifrado.iv=p5ldmBPdd/9pjC0bDC/nSg==",
        "gestopago.api-key=API-KEY-PRUEBA",
        // Timeout corto para probar el caso sin respuesta sin esperar 70 s
        "spring.cloud.openfeign.client.config.gestoPagoTx.read-timeout=1000"
})
@DisplayName("GestoPago (integración HTTP con WireMock)")
class GestoPagoTxIntegracionTest {

    private static final String SEND_TX = "/sistema/service/sendTx.do";
    private static final String CONFIRM_TX = "/sistema/service/confirmTx.do";
    private static final String PAYLOAD = "{\"idProducto\":582,\"idServicio\":133,\"telefono\":\"5555555551\","
            + "\"horaLocal\":\"20261029120000\",\"upc\":\"ABCDE12345\",\"unidad\":\"083\"}";
    private static final String XML_EXITO = """
            <?xml version='1.0' encoding='UTF-8'?>
            <RESPONSE><ID_TX>291437188</ID_TX><NUM_AUTORIZACION>100024988</NUM_AUTORIZACION>
            <SALDO>157731.83</SALDO><MONTO>10.0</MONTO>
            <MENSAJE><CODIGO>01</CODIGO><TEXTO>Operacion realizada con exito</TEXTO><REFERENCIA></REFERENCIA></MENSAJE>
            </RESPONSE>""";

    static final WireMockServer GESTOPAGO = new WireMockServer(wireMockConfig().dynamicPort());

    @Configuration
    @EnableFeignClients(clients = GestoPagoTxClient.class)
    @ImportAutoConfiguration({FeignAutoConfiguration.class, HttpMessageConvertersAutoConfiguration.class,
            JacksonAutoConfiguration.class})
    @Import({GestoPagoTxGateway.class, CifradoGestoPago.class})
    static class Contexto {
        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }
    }

    @Autowired
    private GestoPagoTxGateway gateway;

    @Autowired
    private CifradoGestoPago cifrado;

    @MockBean
    private GestoPagoTokenService tokenService;

    @BeforeAll
    static void iniciar() {
        GESTOPAGO.start();
    }

    @AfterAll
    static void detener() {
        GESTOPAGO.stop();
    }

    @DynamicPropertySource
    static void url(DynamicPropertyRegistry registro) {
        registro.add("gestopago.tx.url", () -> "http://localhost:" + GESTOPAGO.port());
    }

    @BeforeEach
    void limpiar() {
        GESTOPAGO.resetAll();
        when(tokenService.obtenerTokenVigente()).thenReturn(Optional.of("token-vigente"));
    }

    private static Map<String, String> formulario(LoggedRequest peticion) {
        return Arrays.stream(peticion.getBodyAsString().split("&"))
                .map(par -> par.split("=", 2))
                .collect(Collectors.toMap(p -> p[0], p -> URLDecoder.decode(p[1], StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("Envía POST urlencoded con random + signed, Authorization Bearer y X-API-Key, y lee el XML")
    void sendTx_peticionYRespuesta() {
        GESTOPAGO.stubFor(post(urlEqualTo(SEND_TX)).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "text/xml;charset=UTF-8").withBody(XML_EXITO)));

        Resultado res = gateway.enviar(PAYLOAD);

        assertThat(res.codigo()).isEqualTo("01");
        assertThat(res.xml().getNumAutorizacion()).isEqualTo("100024988");
        assertThat(res.xml().idTransaccion()).isEqualTo("291437188");

        GESTOPAGO.verify(postRequestedFor(urlEqualTo(SEND_TX))
                .withHeader("Authorization", equalTo("Bearer token-vigente"))
                .withHeader("X-API-Key", equalTo("API-KEY-PRUEBA"))
                .withHeader("Content-Type", containing("application/x-www-form-urlencoded")));
        List<LoggedRequest> peticiones = GESTOPAGO.findAll(postRequestedFor(urlEqualTo(SEND_TX)));
        Map<String, String> form = formulario(peticiones.get(0));
        assertThat(form).containsOnlyKeys("random", "signed");
        assertThat(form.get("random")).matches("\\d+");
        // Lo que viaja por la red es el payload cifrado, nunca el JSON en claro
        assertThat(peticiones.get(0).getBodyAsString()).doesNotContain("5555555551");
        assertThat(cifrado.descifrar(form.get("signed"))).isEqualTo(PAYLOAD);
    }

    @Test
    @DisplayName("Token vencido (403 de GestoPago): renueva el token y reintenta con el nuevo")
    void sendTx_tokenVencido() {
        when(tokenService.obtenerTokenVigente()).thenReturn(Optional.of("token-viejo"), Optional.of("token-nuevo"));
        GESTOPAGO.stubFor(post(urlEqualTo(SEND_TX)).inScenario("token").whenScenarioStateIs(STARTED)
                .willReturn(aResponse().withStatus(403).withHeader("Content-Type", "application/json")
                        .withBody("{\"success\":false,\"token\":\"EXPIRED\"}"))
                .willSetStateTo("renovado"));
        GESTOPAGO.stubFor(post(urlEqualTo(SEND_TX)).inScenario("token").whenScenarioStateIs("renovado")
                .willReturn(aResponse().withStatus(200).withBody(XML_EXITO)));

        Resultado res = gateway.enviar(PAYLOAD);

        assertThat(res.codigo()).isEqualTo("01");
        verify(tokenService).renovarToken();
        GESTOPAGO.verify(postRequestedFor(urlEqualTo(SEND_TX)).withHeader("Authorization", equalTo("Bearer token-viejo")));
        GESTOPAGO.verify(postRequestedFor(urlEqualTo(SEND_TX)).withHeader("Authorization", equalTo("Bearer token-nuevo")));
    }

    @Test
    @DisplayName("GestoPago tarda más que el timeout: resultado desconocido (enviada, sin respuesta)")
    void sendTx_timeout() {
        GESTOPAGO.stubFor(post(urlEqualTo(SEND_TX)).willReturn(aResponse().withStatus(200)
                .withBody(XML_EXITO).withFixedDelay(3000)));

        Resultado res = gateway.enviar(PAYLOAD);

        assertThat(res.enviada()).isTrue();
        assertThat(res.tieneRespuesta()).isFalse();
    }

    @Test
    @DisplayName("Código 82 de GestoPago se lee como respuesta (se confirmará con confirmTx)")
    void sendTx_codigo82() {
        GESTOPAGO.stubFor(post(urlEqualTo(SEND_TX)).willReturn(aResponse().withStatus(200).withBody("""
                <RESPONSE><NUM_AUTORIZACION>-1</NUM_AUTORIZACION><MENSAJE><CODIGO>82</CODIGO>
                <TEXTO>Tiempo de espera alcanzado, la transacción no ha sido registrada</TEXTO></MENSAJE></RESPONSE>""")));

        assertThat(gateway.enviar(PAYLOAD).codigo()).isEqualTo("82");
    }

    @Test
    @DisplayName("confirmTx reenvía exactamente el mismo signed que sendTx")
    void confirmTx_mismosParametros() {
        GESTOPAGO.stubFor(post(urlEqualTo(SEND_TX)).willReturn(aResponse().withStatus(200).withBody(XML_EXITO)));
        GESTOPAGO.stubFor(post(urlEqualTo(CONFIRM_TX)).willReturn(aResponse().withStatus(200).withBody("""
                <RESPONSE><NUM_AUTORIZACION>100024988</NUM_AUTORIZACION><MENSAJE><CODIGO>06</CODIGO>
                <TEXTO>La transaccion fue aplicada exitosamente.</TEXTO><ID_TX>291437188</ID_TX></MENSAJE></RESPONSE>""")));

        gateway.enviar(PAYLOAD);
        Resultado confirmacion = gateway.confirmar(PAYLOAD);

        assertThat(confirmacion.codigo()).isEqualTo("06");
        String signedEnvio = formulario(GESTOPAGO.findAll(postRequestedFor(urlEqualTo(SEND_TX))).get(0)).get("signed");
        String signedConfirmacion = formulario(GESTOPAGO.findAll(postRequestedFor(urlEqualTo(CONFIRM_TX))).get(0)).get("signed");
        assertThat(signedConfirmacion).isEqualTo(signedEnvio);
    }
}
