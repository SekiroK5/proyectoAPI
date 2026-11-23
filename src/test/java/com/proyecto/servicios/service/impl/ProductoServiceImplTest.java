package com.proyecto.servicios.service.impl;

import com.proyecto.servicios.client.CircuitoGestoPago;
import com.proyecto.servicios.client.LimiteLlamadasGestoPago;
import com.proyecto.servicios.client.ProductoClient;
import com.proyecto.servicios.entity.gestopago.GestoPagoToken;
import com.proyecto.servicios.entity.sf.ProductoCatalogo;
import com.proyecto.servicios.model.producto.ProductoDto;
import com.proyecto.servicios.repository.gestopago.GestoPagoTokenRepository;
import com.proyecto.servicios.service.GestoPagoTokenService;
import feign.FeignException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductoServiceImpl - catálogo en base de datos y cupo diario de getProductList")
class ProductoServiceImplTest {

    @Mock private ProductoClient productoClient;
    @Mock private GestoPagoTokenRepository tokenRepository;
    @Mock private GestoPagoTokenService tokenService;
    @Mock private CatalogoProductos catalogo;
    @Mock private LimiteLlamadasGestoPago limite;

    private final CircuitoGestoPago circuito = new CircuitoGestoPago(
            Clock.fixed(Instant.parse("2026-11-23T12:00:00Z"), ZoneOffset.UTC), 5, 10);
    private ProductoServiceImpl productoService;

    private static final String XML_EXITOSO = """
            <?xml version='1.0' encoding='UTF-8'?>
            <RESPONSE>
                <MENSAJE><CODIGO>01</CODIGO><TEXTO>Operacion realizada con exito</TEXTO></MENSAJE>
                <PRODUCTOS>
                    <producto servicio='Amazon' producto='Amazon $100' idServicio='71' idProducto='200'
                              idCatTipoServicio='10' tipoFront='1' hasDigitoVerificador='false'
                              precio='100.0' showAyuda='false' tipoReferencia='a'>
                        <legend>Texto de ayuda Amazon</legend>
                    </producto>
                    <producto servicio='Zeta Gas' producto='Zeta Gas' idServicio='128' idProducto='5265'
                              idCatTipoServicio='5' tipoFront='2' hasDigitoVerificador='false'
                              precio='10.0' showAyuda='false' tipoReferencia='b'>
                        <legend>Texto de ayuda Zeta Gas</legend>
                    </producto>
                    <producto servicio='Zeta Gas' producto='Zeta Gas' idServicio='128' idProducto='5265'
                              idCatTipoServicio='5' tipoFront='2' hasDigitoVerificador='false'
                              precio='10.0' showAyuda='false' tipoReferencia='b'/>
                </PRODUCTOS>
            </RESPONSE>
            """;

    private static final String XML_ERROR = """
            <RESPONSE><MENSAJE><CODIGO>99</CODIGO><TEXTO>Error en el servicio</TEXTO></MENSAJE><PRODUCTOS/></RESPONSE>
            """;

    @BeforeEach
    void setUp() {
        productoService = new ProductoServiceImpl(productoClient, tokenRepository, tokenService, catalogo, limite, circuito);
        ReflectionTestUtils.setField(productoService, "idDistribuidor", 1);
        ReflectionTestUtils.setField(productoService, "codigoDispositivo", "DISPOSITIVO_001");
        ReflectionTestUtils.setField(productoService, "maxLlamadasDiarias", 2);

        GestoPagoToken token = new GestoPagoToken();
        token.setToken("token-de-prueba-123");
        lenient().when(tokenRepository.findByIdDistribuidorAndCodigoDispositivo(anyInt(), anyString()))
                .thenReturn(Optional.of(token));
        lenient().when(limite.reservar(ProductoServiceImpl.OPERACION, 2)).thenReturn(true);
    }

    @Test
    @DisplayName("obtenerProductos lee de la base de datos y nunca llama a GestoPago")
    void obtenerProductos_soloBaseDeDatos() {
        ProductoCatalogo p = new ProductoCatalogo();
        p.setIdServicio(71);
        p.setIdProducto(200);
        p.setServicio("Amazon");
        p.setPrecio(new BigDecimal("100.00"));
        when(catalogo.listar()).thenReturn(List.of(p));

        List<ProductoDto> resultado = productoService.obtenerProductos();

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).getServicio()).isEqualTo("Amazon");
        assertThat(resultado.get(0).getPrecio()).isEqualTo(100.0);
        verify(productoClient, never()).getProductList(anyString());
    }

    @Test
    @DisplayName("Catálogo vacío: obtenerProductos devuelve lista vacía sin llamar a GestoPago")
    void obtenerProductos_vacioNoLlama() {
        when(catalogo.listar()).thenReturn(List.of());

        assertThat(productoService.obtenerProductos()).isEmpty();
        verify(productoClient, never()).getProductList(anyString());
    }

    @Test
    @DisplayName("Actualización exitosa: guarda el catálogo sin duplicados en la base de datos")
    @SuppressWarnings("unchecked")
    void actualizar_exitoso() {
        when(productoClient.getProductList("Bearer token-de-prueba-123")).thenReturn(XML_EXITOSO);

        productoService.actualizarProductosDesdeApi();

        ArgumentCaptor<List<ProductoCatalogo>> guardados = ArgumentCaptor.forClass(List.class);
        verify(catalogo).reemplazar(guardados.capture());
        assertThat(guardados.getValue()).hasSize(2);
        assertThat(guardados.getValue().get(0).getServicio()).isEqualTo("Amazon");
        assertThat(guardados.getValue().get(1).getPrecio()).isEqualByComparingTo("10.00");
        verify(productoClient, times(1)).getProductList(anyString());
    }

    @Test
    @DisplayName("Cupo diario agotado: no llama a GestoPago ni toca el catálogo")
    void actualizar_cupoAgotado() {
        when(limite.reservar(ProductoServiceImpl.OPERACION, 2)).thenReturn(false);

        productoService.actualizarProductosDesdeApi();

        verify(productoClient, never()).getProductList(anyString());
        verify(catalogo, never()).reemplazar(anyList());
    }

    @Test
    @DisplayName("Llamadas en pausa: no gasta cupo ni llama a GestoPago")
    void actualizar_enPausa() {
        for (int i = 0; i < 5; i++) {
            circuito.registrarFalla("prueba");
        }

        productoService.actualizarProductosDesdeApi();

        verify(limite, never()).reservar(anyString(), anyInt());
        verify(productoClient, never()).getProductList(anyString());
    }

    @Test
    @DisplayName("Sin token en BD y sin poder renovarlo: lanza excepción y no guarda nada")
    void actualizar_sinToken() {
        when(tokenRepository.findByIdDistribuidorAndCodigoDispositivo(anyInt(), anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productoService.actualizarProductosDesdeApi()).isInstanceOf(ResponseStatusException.class);
        verify(productoClient, never()).getProductList(anyString());
        verify(catalogo, never()).reemplazar(anyList());
    }

    @Test
    @DisplayName("Token rechazado (403) y renovación no permitida: no reintenta con el token rechazado")
    void actualizar_tokenRechazado_sinRenovacion() {
        when(productoClient.getProductList(anyString())).thenThrow(mock(FeignException.Forbidden.class));
        when(tokenService.renovarToken()).thenReturn(false);

        assertThatThrownBy(() -> productoService.actualizarProductosDesdeApi()).isInstanceOf(ResponseStatusException.class);
        verify(productoClient, times(1)).getProductList(anyString());
    }

    @Test
    @DisplayName("Token rechazado (403) y renovado: reintenta una vez con el token nuevo")
    void actualizar_tokenRechazado_renovado() {
        when(productoClient.getProductList(eq("Bearer token-de-prueba-123")))
                .thenThrow(mock(FeignException.Forbidden.class))
                .thenReturn(XML_EXITOSO);
        when(tokenService.renovarToken()).thenReturn(true);

        productoService.actualizarProductosDesdeApi();

        verify(productoClient, times(2)).getProductList(anyString());
        verify(catalogo).reemplazar(anyList());
    }

    @Test
    @DisplayName("Timeout o error HTTP: lanza excepción y cuenta como falla para la pausa general")
    void actualizar_errorTecnico() {
        when(productoClient.getProductList(anyString())).thenThrow(mock(FeignException.GatewayTimeout.class));

        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> productoService.actualizarProductosDesdeApi()).isInstanceOf(ResponseStatusException.class);
        }
        assertThat(circuito.permite()).isFalse();
    }

    @Test
    @DisplayName("Código de error de GestoPago: lanza excepción y conserva el catálogo anterior")
    void actualizar_codigoError() {
        when(productoClient.getProductList(anyString())).thenReturn(XML_ERROR);

        assertThatThrownBy(() -> productoService.actualizarProductosDesdeApi()).isInstanceOf(ResponseStatusException.class);
        verify(catalogo, never()).reemplazar(anyList());
    }

    @Test
    @DisplayName("Al arrancar con catálogo ya guardado no llama a GestoPago")
    void cargarSiVacio_conCatalogo() {
        when(catalogo.estaVacio()).thenReturn(false);

        productoService.cargarSiVacio();

        verify(limite, never()).reservar(anyString(), anyInt());
    }

    @Test
    @DisplayName("Al arrancar con catálogo vacío lo carga una vez y no rompe el arranque si falla")
    void cargarSiVacio_vacio() {
        when(catalogo.estaVacio()).thenReturn(true);
        when(productoClient.getProductList(anyString())).thenThrow(mock(FeignException.class));

        productoService.cargarSiVacio();

        verify(productoClient, times(1)).getProductList(anyString());
    }
}
