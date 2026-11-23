package com.proyecto.servicios.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyecto.servicios.client.GestoPagoTxGateway;
import com.proyecto.servicios.client.GestoPagoTxGateway.Resultado;
import com.proyecto.servicios.client.RecursosGestoPago;
import com.proyecto.servicios.dto.request.VerificacionReferenciaRequest;
import com.proyecto.servicios.dto.response.ApiResponse;
import com.proyecto.servicios.dto.response.VerificacionReferenciaResponse;
import com.proyecto.servicios.entity.sf.ProductoCatalogo;
import com.proyecto.servicios.entity.sf.VerificacionReferencia;
import com.proyecto.servicios.enums.Catalogos.ResultadoVerificacion;
import com.proyecto.servicios.model.gestopago.SendTxResponseXml;
import com.proyecto.servicios.repository.sf.ProductoCatalogoRepository;
import com.proyecto.servicios.repository.sf.VerificacionReferenciaRepository;
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
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("VerificarReferenciaHandler - verificación de referencias antes de pagar")
class VerificarReferenciaHandlerTest {

    private static final Integer CLIENTE_ID = 1;
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-11-23T18:00:00Z"), ZoneOffset.UTC);

    @Mock private ProductoCatalogoRepository catalogoRepository;
    @Mock private VerificacionReferenciaRepository verificacionRepository;
    @Mock private GestoPagoTxGateway gateway;

    private VerificarReferenciaHandler handler;

    @BeforeEach
    void setUp() {
        handler = new VerificarReferenciaHandler(catalogoRepository, verificacionRepository, gateway,
                new RecursosGestoPago("https://gestopago.portalventas.net"), new ObjectMapper(), RELOJ, 20);
        lenient().when(verificacionRepository.save(any(VerificacionReferencia.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static ProductoCatalogo producto(int idServicio, int idProducto, int tipoFront, String tipoReferencia) {
        ProductoCatalogo p = new ProductoCatalogo();
        p.setIdServicio(idServicio);
        p.setIdProducto(idProducto);
        p.setTipoFront(tipoFront);
        p.setTipoReferencia(tipoReferencia);
        p.setServicio("Servicio " + idServicio);
        p.setProducto("Producto " + idProducto);
        return p;
    }

    private void enCatalogo(ProductoCatalogo p) {
        when(catalogoRepository.findFirstByIdServicioAndIdProducto(p.getIdServicio(), p.getIdProducto())).thenReturn(Optional.of(p));
    }

    private static VerificacionReferenciaRequest request(int idServicio, int idProducto, String referencia) {
        VerificacionReferenciaRequest r = new VerificacionReferenciaRequest();
        r.setIdServicio(idServicio);
        r.setIdProducto(idProducto);
        r.setReferencia(referencia);
        return r;
    }

    private static Resultado respuesta(String codigo, String texto, String saldo) {
        SendTxResponseXml xml = new SendTxResponseXml();
        SendTxResponseXml.Mensaje m = new SendTxResponseXml.Mensaje();
        m.setCodigo(codigo);
        m.setTexto(texto);
        m.setSaldo(saldo);
        xml.setMensaje(m);
        return new Resultado(xml, "<RESPONSE/>", null, true);
    }

    private ApiResponse<VerificacionReferenciaResponse> ejecutar(VerificacionReferenciaRequest r) {
        return handler.handle(new VerificarReferenciaComando(CLIENTE_ID, r));
    }

    @Test
    @DisplayName("Servicio con verifyReference: consulta a GestoPago con el producto tipoFront 4 y guarda folio VALIDA")
    void valida_conGestoPago() {
        enCatalogo(producto(108, 300, 2, "b"));
        when(catalogoRepository.findFirstByIdServicioAndTipoFront(108, 4)).thenReturn(Optional.of(producto(108, 272, 4, "b")));
        when(gateway.verificarReferencia(anyString())).thenReturn(respuesta("01", "Referencia Valida", "1034.5"));

        ApiResponse<VerificacionReferenciaResponse> res = ejecutar(request(108, 300, "5610440665"));

        assertThat(res.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(res.getDatos().getResultado()).isEqualTo(ResultadoVerificacion.VALIDA);
        assertThat(res.getDatos().getVerificadaConGestoPago()).isTrue();
        assertThat(res.getDatos().getSaldoPendiente()).isEqualByComparingTo("1034.5");
        assertThat(res.getDatos().getFolio()).isNotBlank();
        assertThat(res.getDatos().getExpira()).isEqualTo(LocalDateTime.now(RELOJ).plusMinutes(15));
        assertThat(res.getDatos().getLogoUrl()).endsWith("/servicios/108.png");
        verify(gateway).verificarReferencia("{\"idProducto\":272,\"idServicio\":108,\"referencia\":\"5610440665\"}");
    }

    @Test
    @DisplayName("Referencia rechazada por GestoPago: 422 con su mensaje y se guarda como INVALIDA")
    void invalida() {
        enCatalogo(producto(107, 271, 4, "b"));
        when(gateway.verificarReferencia(anyString())).thenReturn(respuesta("02", "Referencia Invalida", null));

        ApiResponse<VerificacionReferenciaResponse> res = ejecutar(request(107, 271, "TMX00000000000000000000000"));

        assertThat(res.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(res.getMensaje()).contains("Referencia Invalida");
        ArgumentCaptor<VerificacionReferencia> guardada = ArgumentCaptor.forClass(VerificacionReferencia.class);
        verify(verificacionRepository).save(guardada.capture());
        assertThat(guardada.getValue().getResultado()).isEqualTo(ResultadoVerificacion.INVALIDA);
    }

    @Test
    @DisplayName("Servicio sin verifyReference en GestoPago: FORMATO_VALIDO sin llamar a la API")
    void soloFormato() {
        enCatalogo(producto(76, 205, 2, "b"));
        when(catalogoRepository.findFirstByIdServicioAndTipoFront(76, 4)).thenReturn(Optional.empty());

        ApiResponse<VerificacionReferenciaResponse> res = ejecutar(request(76, 205, "25720381"));

        assertThat(res.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(res.getDatos().getResultado()).isEqualTo(ResultadoVerificacion.FORMATO_VALIDO);
        assertThat(res.getDatos().getVerificadaConGestoPago()).isFalse();
        verify(gateway, never()).verificarReferencia(anyString());
    }

    @Test
    @DisplayName("Sin respuesta de GestoPago: 503 y no se guarda ninguna verificación")
    void sinRespuesta() {
        enCatalogo(producto(108, 272, 4, "b"));
        when(gateway.verificarReferencia(anyString())).thenReturn(new Resultado(null, null, "timeout", true));

        ApiResponse<VerificacionReferenciaResponse> res = ejecutar(request(108, 272, "5610440665"));

        assertThat(res.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        verify(verificacionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Referencia tipo teléfono (tipoReferencia a) con formato incorrecto: 400 sin llamar a GestoPago")
    void formatoTelefono() {
        enCatalogo(producto(108, 272, 4, "a"));

        ApiResponse<VerificacionReferenciaResponse> res = ejecutar(request(108, 272, "12345"));

        assertThat(res.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(res.getMensaje()).isEqualTo(VerificarReferenciaHandler.MSG_TELEFONO);
        verify(gateway, never()).verificarReferencia(anyString());
    }

    @Test
    @DisplayName("Producto inexistente (404) o recarga sin referencia (422)")
    void productoNoValido() {
        when(catalogoRepository.findFirstByIdServicioAndIdProducto(1, 1)).thenReturn(Optional.empty());
        assertThat(ejecutar(request(1, 1, "123")).getStatus()).isEqualTo(HttpStatus.NOT_FOUND);

        enCatalogo(producto(133, 582, 1, "a"));
        assertThat(ejecutar(request(133, 582, "5555555551")).getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    @DisplayName("Más de 20 verificaciones en una hora: 429 sin llamar a GestoPago")
    void limitePorHora() {
        enCatalogo(producto(108, 272, 4, "b"));
        when(verificacionRepository.countByClienteIdAndFechaCreacionAfter(eq(CLIENTE_ID), any())).thenReturn(20L);

        assertThat(ejecutar(request(108, 272, "5610440665")).getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        verify(gateway, never()).verificarReferencia(anyString());
    }
}
