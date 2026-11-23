package com.proyecto.servicios.service.impl;

import com.proyecto.servicios.client.RecursosGestoPago;
import com.proyecto.servicios.dto.request.DetalleTransaccionRequest;
import com.proyecto.servicios.dto.request.HistorialTransaccionesRequest;
import com.proyecto.servicios.dto.response.ApiResponse;
import com.proyecto.servicios.dto.response.PaginaResponse;
import com.proyecto.servicios.dto.response.TransaccionResponse;
import com.proyecto.servicios.entity.sf.Cuenta;
import com.proyecto.servicios.entity.sf.ProductoCatalogo;
import com.proyecto.servicios.entity.sf.Transaccion;
import com.proyecto.servicios.enums.Catalogos.EstatusTransaccion;
import com.proyecto.servicios.repository.sf.ProductoCatalogoRepository;
import com.proyecto.servicios.repository.sf.TransaccionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TransaccionServiceImpl - historial y detalle de pagos del usuario")
class TransaccionServiceImplTest {

    @Mock private TransaccionRepository transaccionRepository;
    @Mock private ProductoCatalogoRepository catalogoRepository;

    private TransaccionServiceImpl servicio;

    @BeforeEach
    void setUp() {
        servicio = new TransaccionServiceImpl(transaccionRepository, catalogoRepository,
                new RecursosGestoPago("https://gestopago.portalventas.net"));
        ProductoCatalogo telcel = new ProductoCatalogo();
        telcel.setIdServicio(133);
        telcel.setIdProducto(582);
        telcel.setServicio("Telcel");
        telcel.setProducto("Telcel $10");
        telcel.setIdCatTipoServicio(2);
        lenient().when(catalogoRepository.findAll()).thenReturn(List.of(telcel));
    }

    private static Transaccion transaccion(int id) {
        Cuenta cuenta = new Cuenta();
        cuenta.setNumeroCuenta("4152313748382907");
        cuenta.setSaldo(new BigDecimal("990.00"));
        Transaccion tx = new Transaccion();
        tx.setId(id);
        tx.setCuenta(cuenta);
        tx.setIdServicio(133);
        tx.setIdProducto(582);
        tx.setTelefono("5555555551");
        tx.setMonto(new BigDecimal("10.00"));
        tx.setEstatus(EstatusTransaccion.APLICADA);
        tx.setNumAutorizacion("100046644");
        tx.setTextoRespuesta("Operacion realizada con exito");
        return tx;
    }

    @Test
    @DisplayName("Historial: solo del cliente, del más reciente al más antiguo, con servicio, categoría y logotipo")
    void historial() {
        when(transaccionRepository.findByCuenta_Cliente_Id(eq(7), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(transaccion(2), transaccion(1))));

        ApiResponse<PaginaResponse<TransaccionResponse>> res = servicio.historial(7, new HistorialTransaccionesRequest());

        assertThat(res.getStatus()).isEqualTo(HttpStatus.OK);
        TransaccionResponse primero = res.getDatos().getContenido().get(0);
        assertThat(primero.getServicio()).isEqualTo("Telcel");
        assertThat(primero.getCategoria()).isEqualTo("Telcel");
        assertThat(primero.getLogoUrl()).endsWith("/servicios/133.png");
        assertThat(primero.getNumAutorizacion()).isEqualTo("100046644");

        ArgumentCaptor<Pageable> pagina = ArgumentCaptor.forClass(Pageable.class);
        verify(transaccionRepository).findByCuenta_Cliente_Id(eq(7), pagina.capture());
        assertThat(pagina.getValue().getSort().getOrderFor("fechaCreacion").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    @DisplayName("Historial filtrado por estatus")
    void historialPorEstatus() {
        HistorialTransaccionesRequest req = new HistorialTransaccionesRequest();
        req.setEstatus(EstatusTransaccion.RECHAZADA);
        when(transaccionRepository.findByCuenta_Cliente_IdAndEstatus(eq(7), eq(EstatusTransaccion.RECHAZADA), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        ApiResponse<PaginaResponse<TransaccionResponse>> res = servicio.historial(7, req);

        assertThat(res.getMensaje()).isEqualTo("Aún no tiene pagos registrados.");
    }

    @Test
    @DisplayName("Detalle de una transacción propia")
    void detalle() {
        when(transaccionRepository.findByIdAndCuenta_Cliente_Id(5, 7)).thenReturn(Optional.of(transaccion(5)));
        DetalleTransaccionRequest req = new DetalleTransaccionRequest();
        req.setId(5);

        ApiResponse<TransaccionResponse> res = servicio.detalle(7, req);

        assertThat(res.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(res.getDatos().getId()).isEqualTo(5);
        assertThat(res.getDatos().getMensajeGestoPago()).isEqualTo("Operacion realizada con exito");
    }

    @Test
    @DisplayName("Detalle de una transacción de otro cliente: 404 (no revela que existe)")
    void detalleAjeno() {
        when(transaccionRepository.findByIdAndCuenta_Cliente_Id(5, 8)).thenReturn(Optional.empty());
        DetalleTransaccionRequest req = new DetalleTransaccionRequest();
        req.setId(5);

        assertThat(servicio.detalle(8, req).getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
