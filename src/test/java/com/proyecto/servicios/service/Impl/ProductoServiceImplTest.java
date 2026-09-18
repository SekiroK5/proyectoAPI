package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.client.ProductoClient;
import com.proyecto.servicios.config.exception.IntegracionException;
import com.proyecto.servicios.entity.gestopago.GestoPagoToken;
import com.proyecto.servicios.model.producto.ProductoDto;
import com.proyecto.servicios.repositorys.gestopago.GestoPagoTokenRepository;
import com.proyecto.servicios.service.GestoPagoTokenService;
import feign.FeignException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductoServiceImpl - Pruebas Unitarias")
class ProductoServiceImplTest {

    @Mock
    private ProductoClient productoClient;

    @Mock
    private GestoPagoTokenRepository tokenRepository;

    @Mock
    private GestoPagoTokenService tokenService;

    @InjectMocks
    private ProductoServiceImpl productoService;

    private static final String XML_EXITOSO = """
            <?xml version='1.0' encoding='UTF-8'?>
            <RESPONSE>
                <MENSAJE>
                    <CODIGO>01</CODIGO>
                    <TEXTO>Operacion realizada con exito</TEXTO>
                </MENSAJE>
                <PRODUCTOS>
                    <producto servicio='Amazon' producto='Amazon $100'
                              idServicio='71' idProducto='200' idCatTipoServicio='10'
                              tipoFront='1' hasDigitoVerificador='false'
                              precio='100.0' showAyuda='false' tipoReferencia='a'>
                        <legend>Texto de ayuda Amazon</legend>
                    </producto>
                    <producto servicio='Zeta Gas' producto='Zeta Gas'
                              idServicio='128' idProducto='5265' idCatTipoServicio='5'
                              tipoFront='2' hasDigitoVerificador='false'
                              precio='10.0' showAyuda='false' tipoReferencia='b'>
                        <legend>Texto de ayuda Zeta Gas</legend>
                    </producto>
                </PRODUCTOS>
            </RESPONSE>
            """;

    private static final String XML_ERROR = """
            <?xml version='1.0' encoding='UTF-8'?>
            <RESPONSE>
                <MENSAJE>
                    <CODIGO>99</CODIGO>
                    <TEXTO>Error en el servicio</TEXTO>
                </MENSAJE>
                <PRODUCTOS/>
            </RESPONSE>
            """;

    private GestoPagoToken tokenActivo;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(productoService, "idDistribuidor", 1);
        ReflectionTestUtils.setField(productoService, "codigoDispositivo", "DISPOSITIVO_001");

        tokenActivo = new GestoPagoToken();
        tokenActivo.setToken("token-de-prueba-123");
        tokenActivo.setIdDistribuidor(1);
        tokenActivo.setCodigoDispositivo("DISPOSITIVO_001");
        tokenActivo.setActivo(true);
    }

    @Test
    @DisplayName("✅ Debe obtener lista de productos exitosamente cuando hay token y API responde OK")
    void actualizarProductosDesdeApi_exitoso() {
        // Given
        when(tokenRepository.findByIdDistribuidorAndCodigoDispositivo(anyInt(), anyString()))
                .thenReturn(Optional.of(tokenActivo));
        when(productoClient.getProductList("Bearer token-de-prueba-123"))
                .thenReturn(XML_EXITOSO);

        // When
        productoService.actualizarProductosDesdeApi();
        List<ProductoDto> resultado = productoService.obtenerProductos();

        // Then
        assertThat(resultado).hasSize(2);
        assertThat(resultado.get(0).getServicio()).isEqualTo("Amazon");
        assertThat(resultado.get(0).getIdProducto()).isEqualTo(200);
        assertThat(resultado.get(0).getPrecio()).isEqualTo(100.0);
        assertThat(resultado.get(1).getServicio()).isEqualTo("Zeta Gas");
        verify(productoClient, times(1)).getProductList(anyString());
    }

    @Test
    @DisplayName("❌ Debe lanzar IntegracionException cuando no hay token en BD")
    void actualizarProductosDesdeApi_sinToken_lanzaExcepcion() {
        // Given
        when(tokenRepository.findByIdDistribuidorAndCodigoDispositivo(anyInt(), anyString()))
                .thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> productoService.actualizarProductosDesdeApi())
                .isInstanceOf(IntegracionException.class)
                .hasMessageContaining("No hay token activo");

        verify(productoClient, never()).getProductList(anyString());
    }

    @Test
    @DisplayName("❌ Debe lanzar IntegracionException cuando el token esta expirado (403)")
    void actualizarProductosDesdeApi_tokenExpirado_lanzaExcepcion() {
        // Given
        when(tokenRepository.findByIdDistribuidorAndCodigoDispositivo(anyInt(), anyString()))
                .thenReturn(Optional.of(tokenActivo));
        when(productoClient.getProductList(anyString()))
                .thenThrow(mock(FeignException.Forbidden.class));

        // When & Then
        assertThatThrownBy(() -> productoService.actualizarProductosDesdeApi())
                .isInstanceOf(IntegracionException.class)
                .hasMessageContaining("Token no valido o expirado");
    }

    @Test
    @DisplayName("❌ Debe lanzar IntegracionException cuando hay timeout en la llamada")
    void actualizarProductosDesdeApi_timeout_lanzaExcepcion() {
        // Given
        when(tokenRepository.findByIdDistribuidorAndCodigoDispositivo(anyInt(), anyString()))
                .thenReturn(Optional.of(tokenActivo));
        when(productoClient.getProductList(anyString()))
                .thenThrow(mock(FeignException.GatewayTimeout.class));

        // When & Then
        assertThatThrownBy(() -> productoService.actualizarProductosDesdeApi())
                .isInstanceOf(IntegracionException.class)
                .hasMessageContaining("Timeout");
    }

    @Test
    @DisplayName("❌ Debe lanzar IntegracionException cuando el servicio no esta disponible")
    void actualizarProductosDesdeApi_servicioNoDisponible_lanzaExcepcion() {
        // Given
        when(tokenRepository.findByIdDistribuidorAndCodigoDispositivo(anyInt(), anyString()))
                .thenReturn(Optional.of(tokenActivo));
        when(productoClient.getProductList(anyString()))
                .thenThrow(mock(FeignException.class));

        // When & Then
        assertThatThrownBy(() -> productoService.actualizarProductosDesdeApi())
                .isInstanceOf(IntegracionException.class)
                .hasMessageContaining("Error de comunicacion");
    }

    @Test
    @DisplayName("❌ Debe lanzar IntegracionException cuando la API retorna codigo de error")
    void actualizarProductosDesdeApi_codigoErrorEnRespuesta_lanzaExcepcion() {
        // Given
        when(tokenRepository.findByIdDistribuidorAndCodigoDispositivo(anyInt(), anyString()))
                .thenReturn(Optional.of(tokenActivo));
        when(productoClient.getProductList(anyString()))
                .thenReturn(XML_ERROR);

        // When & Then
        assertThatThrownBy(() -> productoService.actualizarProductosDesdeApi())
                .isInstanceOf(IntegracionException.class)
                .hasMessageContaining("Error en el servicio");
    }

    @Test
    @DisplayName("✅ Debe intentar actualizar desde API cuando la cache esta vacia")
    void obtenerProductos_cacheVacia_llama_api() {
        // Given
        when(tokenRepository.findByIdDistribuidorAndCodigoDispositivo(anyInt(), anyString()))
                .thenReturn(Optional.of(tokenActivo));
        when(productoClient.getProductList(anyString()))
                .thenReturn(XML_EXITOSO);

        // When
        List<ProductoDto> resultado = productoService.obtenerProductos();

        // Then
        assertThat(resultado).isNotEmpty();
        verify(productoClient, times(1)).getProductList(anyString());
    }
}
