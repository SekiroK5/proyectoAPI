package com.proyecto.servicios.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyecto.servicios.client.GestoPagoTxGateway;
import com.proyecto.servicios.client.GestoPagoTxGateway.Resultado;
import com.proyecto.servicios.dto.request.TransaccionRequest;
import com.proyecto.servicios.dto.response.ApiResponse;
import com.proyecto.servicios.dto.response.TransaccionResponse;
import com.proyecto.servicios.entity.sf.Cliente;
import com.proyecto.servicios.entity.sf.Cuenta;
import com.proyecto.servicios.entity.sf.Transaccion;
import com.proyecto.servicios.enums.Catalogos.EstatusCuenta;
import com.proyecto.servicios.enums.Catalogos.EstatusTransaccion;
import com.proyecto.servicios.enums.Catalogos.MetodoA2F;
import com.proyecto.servicios.model.gestopago.SendTxResponseXml;
import com.proyecto.servicios.model.producto.ProductoDto;
import com.proyecto.servicios.repository.sf.ClienteRepository;
import com.proyecto.servicios.repository.sf.CuentaRepository;
import com.proyecto.servicios.repository.sf.TransaccionRepository;
import com.proyecto.servicios.seguridad.IntentosFallidos;
import com.proyecto.servicios.seguridad.Totp;
import com.proyecto.servicios.service.ProductoService;
import com.proyecto.servicios.service.impl.RegistroTransacciones;
import org.apache.commons.codec.binary.Base32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SendTxHandler - flujo de pago con GestoPago")
class SendTxHandlerTest {

    private static final String NUMERO_CUENTA = "4152313748382907";
    private static final Integer CLIENTE_ID = 1;
    /** 2026-10-23 18:00:00 UTC = 12:00:00 en America/Mexico_City (UTC-6). */
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-10-23T18:00:00Z"), ZoneOffset.UTC);

    @Mock private CuentaRepository cuentaRepository;
    @Mock private TransaccionRepository transaccionRepository;
    @Mock private ClienteRepository clienteRepository;
    @Mock private ProductoService productoService;
    @Mock private RegistroTransacciones registro;
    @Mock private GestoPagoTxGateway gateway;

    private final Totp totp = new Totp(RELOJ);
    private SendTxHandler handler;
    private Cliente cliente;
    private Cuenta cuenta;

    @BeforeEach
    void setUp() {
        handler = new SendTxHandler(cuentaRepository, clienteRepository, transaccionRepository, productoService, totp,
                new IntentosFallidos(clienteRepository, RELOJ, 5, 15), registro, gateway,
                new ObjectMapper(), RELOJ, "083", "America/Mexico_City");

        cliente = new Cliente();
        cliente.setId(CLIENTE_ID);
        cliente.setActivo(true);
        cliente.setA2fMetodo(MetodoA2F.TOTP);
        cliente.setA2fSecreto(totp.generarSecreto());
        cliente.setA2fActivo(true);
        cuenta = new Cuenta();
        cuenta.setId(10);
        cuenta.setNumeroCuenta(NUMERO_CUENTA);
        cuenta.setSaldo(new BigDecimal("500.00"));
        cuenta.setEstatus(EstatusCuenta.ACTIVA);
        cuenta.setCliente(cliente);

        lenient().when(cuentaRepository.findByNumeroCuenta(NUMERO_CUENTA)).thenReturn(Optional.of(cuenta));
        // Simula el UPDATE condicional: solo marca el paso si es posterior al ultimo usado
        lenient().when(clienteRepository.marcarPasoA2F(eq(CLIENTE_ID), anyLong())).thenAnswer(inv -> {
            long paso = inv.getArgument(1);
            if (cliente.getA2fUltimoPaso() != null && cliente.getA2fUltimoPaso() >= paso) {
                return 0;
            }
            cliente.setA2fUltimoPaso(paso);
            return 1;
        });
        lenient().when(productoService.obtenerProductos()).thenReturn(List.of(
                producto(133, 582, 1, 2, 10.0),      // recarga Telcel $10
                producto(76, 205, 2, 5, 10.0),       // pago de servicio
                producto(178, 7030, 1, 11, 300.0),   // tarjeta de regalo (canjeable)
                producto(300, 900, 30, 8, 0.0)));    // cash-in (no soportado)
        // apartarSaldo devuelve la transaccion recibida con id y cuenta, como lo haria la base de datos
        lenient().when(registro.apartarSaldo(eq(10), any(Transaccion.class))).thenAnswer(inv -> {
            Transaccion tx = inv.getArgument(1);
            tx.setId(99);
            tx.setCuenta(cuenta);
            tx.setEstatus(EstatusTransaccion.PENDIENTE);
            return Optional.of(tx);
        });
    }

    // ======================== Datos de prueba ========================

    private static ProductoDto producto(int idServicio, int idProducto, int tipoFront, int idCatTipoServicio, double precio) {
        ProductoDto p = new ProductoDto();
        p.setIdServicio(idServicio);
        p.setIdProducto(idProducto);
        p.setTipoFront(tipoFront);
        p.setIdCatTipoServicio(idCatTipoServicio);
        p.setPrecio(precio);
        return p;
    }

    private String codigoActual() {
        return Totp.generarCodigo(new Base32().decode(cliente.getA2fSecreto()), RELOJ.millis() / 1000 / 30);
    }

    private TransaccionRequest recarga() {
        TransaccionRequest req = new TransaccionRequest();
        req.setNumeroCuenta(NUMERO_CUENTA);
        req.setIdServicio(133);
        req.setIdProducto(582);
        req.setMonto(new BigDecimal("10.00"));
        req.setTelefono("5555555551");
        req.setCodigoA2F(codigoActual());
        return req;
    }

    private ApiResponse<TransaccionResponse> ejecutar(TransaccionRequest req) {
        return handler.handle(new SendTxComando(CLIENTE_ID, req));
    }

    private static Resultado respuesta(String codigo, String texto) {
        SendTxResponseXml xml = new SendTxResponseXml();
        SendTxResponseXml.Mensaje mensaje = new SendTxResponseXml.Mensaje();
        mensaje.setCodigo(codigo);
        mensaje.setTexto(texto);
        xml.setMensaje(mensaje);
        xml.setNumAutorizacion("100024988");
        return new Resultado(xml, "<RESPONSE/>", null, true);
    }

    /** Simula el cierre de la transaccion en RegistroTransacciones copiando el estatus y la respuesta. */
    private void registroCierraCon(EstatusTransaccion estatus) {
        lenient().when(registro.aplicar(eq(99), any())).thenAnswer(inv -> cerrar(estatus, inv.getArgument(1)));
        lenient().when(registro.rechazar(eq(99), any())).thenAnswer(inv -> cerrar(estatus, inv.getArgument(1)));
        lenient().when(registro.marcarPorConfirmar(eq(99), any())).thenAnswer(inv -> cerrar(estatus, inv.getArgument(1)));
    }

    private Transaccion cerrar(EstatusTransaccion estatus, Resultado resultado) {
        Transaccion tx = new Transaccion();
        tx.setId(99);
        tx.setCuenta(cuenta);
        tx.setEstatus(estatus);
        tx.setMonto(new BigDecimal("10.00"));
        if (resultado.xml() != null) {
            tx.setCodigoRespuesta(resultado.codigo());
            tx.setTextoRespuesta(resultado.xml().texto());
            tx.setNumAutorizacion(resultado.xml().getNumAutorizacion());
        }
        return tx;
    }

    private String payloadEnviado() {
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(gateway).enviar(payload.capture());
        return payload.getValue();
    }

    // ======================== Validaciones previas ========================

    @Test
    @DisplayName("Cuenta inexistente: 404 sin tocar el saldo")
    void cuentaInexistente() {
        TransaccionRequest req = recarga();
        req.setNumeroCuenta("0000000000000000");

        assertThat(ejecutar(req).getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        verify(registro, never()).apartarSaldo(any(), any());
    }

    @Test
    @DisplayName("La cuenta de otro cliente: 403")
    void cuentaAjena() {
        ApiResponse<TransaccionResponse> res = handler.handle(new SendTxComando(2, recarga()));

        assertThat(res.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(res.getMensaje()).isEqualTo(SendTxHandler.MSG_CUENTA_AJENA);
    }

    @Test
    @DisplayName("Cliente dado de baja o cuenta inactiva: 403")
    void clienteInactivo() {
        cliente.setActivo(false);
        assertThat(ejecutar(recarga()).getMensaje()).isEqualTo(SendTxHandler.MSG_INACTIVO);

        cliente.setActivo(true);
        cuenta.setEstatus(EstatusCuenta.INACTIVA);
        assertThat(ejecutar(recarga()).getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("Saldo insuficiente: 409 antes de pedir el 2FA y sin llamar a GestoPago")
    void saldoInsuficiente() {
        cuenta.setSaldo(new BigDecimal("9.99"));

        ApiResponse<TransaccionResponse> res = ejecutar(recarga());

        assertThat(res.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(res.getMensaje()).isEqualTo(SendTxHandler.MSG_SALDO);
        verify(gateway, never()).enviar(anyString());
    }

    @Test
    @DisplayName("Si otro pago simultáneo consumió el saldo al apartarlo: 409")
    void saldoConsumidoAlApartar() {
        when(registro.apartarSaldo(eq(10), any(Transaccion.class))).thenReturn(Optional.empty());

        assertThat(ejecutar(recarga()).getStatus()).isEqualTo(HttpStatus.CONFLICT);
        verify(gateway, never()).enviar(anyString());
    }

    @Test
    @DisplayName("Producto fuera del catálogo: 404")
    void productoInexistente() {
        TransaccionRequest req = recarga();
        req.setIdProducto(1);

        assertThat(ejecutar(req).getMensaje()).isEqualTo(SendTxHandler.MSG_PRODUCTO);
    }

    @Test
    @DisplayName("Recarga con monto distinto al precio del producto: 400")
    void recargaMontoDistinto() {
        TransaccionRequest req = recarga();
        req.setMonto(new BigDecimal("20.00"));

        ApiResponse<TransaccionResponse> res = ejecutar(req);

        assertThat(res.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(res.getMensaje()).contains("10.00");
    }

    @Test
    @DisplayName("Recarga sin teléfono y pago de servicio sin referencia: 400")
    void camposPorTipoFront() {
        TransaccionRequest sinTelefono = recarga();
        sinTelefono.setTelefono(null);
        assertThat(ejecutar(sinTelefono).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);

        TransaccionRequest servicio = recarga();
        servicio.setIdServicio(76);
        servicio.setIdProducto(205);
        assertThat(ejecutar(servicio).getMensaje()).contains("referencia");
    }

    @Test
    @DisplayName("tipoFront no soportado (cash-in): 422")
    void tipoFrontNoSoportado() {
        TransaccionRequest req = recarga();
        req.setIdServicio(300);
        req.setIdProducto(900);

        assertThat(ejecutar(req).getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    @DisplayName("Código 2FA incorrecto: 401, cuenta como intento fallido y no se envía nada")
    void a2fIncorrecto() {
        TransaccionRequest req = recarga();
        req.setCodigoA2F(codigoActual().equals("000000") ? "111111" : "000000");

        ApiResponse<TransaccionResponse> res = ejecutar(req);

        assertThat(res.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(cliente.getIntentosFallidos()).isEqualTo(1);
        verify(registro, never()).apartarSaldo(any(), any());
    }

    @Test
    @DisplayName("Cliente sin 2FA configurado: 409")
    void a2fSinConfigurar() {
        cliente.setA2fActivo(false);

        assertThat(ejecutar(recarga()).getMensaje()).isEqualTo(SendTxHandler.MSG_A2F_SIN_CONFIGURAR);
    }

    @Test
    @DisplayName("El mismo código 2FA no autoriza dos pagos")
    void a2fReutilizado() {
        registroCierraCon(EstatusTransaccion.APLICADA);
        when(gateway.enviar(anyString())).thenReturn(respuesta("01", "Operacion realizada con exito"));
        TransaccionRequest req = recarga();
        ejecutar(req);

        assertThat(ejecutar(req).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Doble clic: si otra petición simultánea ya usó el código, 409 y no se aparta saldo")
    void dobleClic_codigoEnUso() {
        TransaccionRequest req = recarga();
        // La otra peticion marco el paso en la base de datos despues de que esta leyo al cliente
        when(clienteRepository.marcarPasoA2F(eq(CLIENTE_ID), anyLong())).thenReturn(0);

        ApiResponse<TransaccionResponse> res = ejecutar(req);

        assertThat(res.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(res.getMensaje()).isEqualTo(SendTxHandler.MSG_A2F_EN_USO);
        verify(registro, never()).apartarSaldo(any(), any());
        verify(gateway, never()).enviar(anyString());
    }

    // ======================== Envío y respuesta ========================

    @Test
    @DisplayName("Recarga exitosa: payload SendTx exacto, 200 'Pago exitoso.' y cargo aplicado")
    void recargaExitosa() throws Exception {
        registroCierraCon(EstatusTransaccion.APLICADA);
        when(gateway.enviar(anyString())).thenReturn(respuesta("01", "Operacion realizada con exito"));

        ApiResponse<TransaccionResponse> res = ejecutar(recarga());

        assertThat(res.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(res.getMensaje()).isEqualTo(SendTxHandler.MSG_PAGO_EXITOSO);
        assertThat(res.getDatos().getEstatus()).isEqualTo(EstatusTransaccion.APLICADA);
        assertThat(res.getDatos().getNumAutorizacion()).isEqualTo("100024988");
        verify(registro).aplicar(eq(99), any());

        String payload = payloadEnviado();
        // Mismo orden y nombres que la documentacion; sin referencia ni montoPago en recargas
        assertThat(payload).matches("\\{\"idProducto\":582,\"idServicio\":133,\"telefono\":\"5555555551\","
                + "\"horaLocal\":\"20261023120000\",\"upc\":\"[A-Z0-9]{10}\",\"unidad\":\"083\"}");
    }

    @Test
    @DisplayName("Pago de servicio: teléfono fijo 1111111111, referencia y montoPago")
    void pagoServicio() {
        registroCierraCon(EstatusTransaccion.APLICADA);
        when(gateway.enviar(anyString())).thenReturn(respuesta("01", "Operacion realizada con exito"));
        TransaccionRequest req = recarga();
        req.setIdServicio(76);
        req.setIdProducto(205);
        req.setMonto(new BigDecimal("601.99"));
        req.setReferencia("25720381");
        cuenta.setSaldo(new BigDecimal("1000.00"));

        assertThat(ejecutar(req).getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(payloadEnviado()).contains("\"telefono\":\"1111111111\"", "\"referencia\":\"25720381\"", "\"montoPago\":601.99");
    }

    @Test
    @DisplayName("Tarjeta de regalo (idCatTipoServicio 11): teléfono fijo #1111111111")
    void canjeable() {
        registroCierraCon(EstatusTransaccion.APLICADA);
        when(gateway.enviar(anyString())).thenReturn(respuesta("01", "Operacion realizada con exito"));
        TransaccionRequest req = recarga();
        req.setIdServicio(178);
        req.setIdProducto(7030);
        req.setMonto(new BigDecimal("300.00"));
        req.setTelefono(null);

        ejecutar(req);

        assertThat(payloadEnviado()).contains("\"telefono\":\"#1111111111\"");
    }

    @Test
    @DisplayName("Código 02 (saldo del distribuidor): 503 con mensaje propio, sin exponer el texto de GestoPago")
    void rechazada_saldoDistribuidor() {
        registroCierraCon(EstatusTransaccion.RECHAZADA);
        when(gateway.enviar(anyString())).thenReturn(respuesta("02", "Saldo insuficiente"));

        ApiResponse<TransaccionResponse> res = ejecutar(recarga());

        assertThat(res.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(res.getMensaje()).contains("no está disponible", "No se realizó ningún cargo", "GP-02")
                .doesNotContain("Saldo insuficiente");
        verify(registro).rechazar(eq(99), any());
    }

    @Test
    @DisplayName("Código 06 (duplicada en GestoPago): 409")
    void rechazada_duplicada() {
        registroCierraCon(EstatusTransaccion.RECHAZADA);
        when(gateway.enviar(anyString())).thenReturn(respuesta("06", "Transaccion aplicada anteriormente"));

        ApiResponse<TransaccionResponse> res = ejecutar(recarga());

        assertThat(res.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(res.getMensaje()).contains("operación igual", "GP-06");
    }

    @Test
    @DisplayName("Otro código de rechazo: 422 con mensaje genérico propio")
    void rechazada_otroCodigo() {
        registroCierraCon(EstatusTransaccion.RECHAZADA);
        when(gateway.enviar(anyString())).thenReturn(respuesta("13", "Referencia invalida para el servicio XYZ"));

        ApiResponse<TransaccionResponse> res = ejecutar(recarga());

        assertThat(res.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(res.getMensaje()).contains("GP-13").doesNotContain("XYZ");
    }

    @Test
    @DisplayName("Tiempo de espera (código 82): 202 y queda por confirmar con el saldo apartado")
    void timeoutCodigo82() {
        registroCierraCon(EstatusTransaccion.PENDIENTE_CONFIRMACION);
        when(gateway.enviar(anyString())).thenReturn(respuesta("82", "Tiempo de espera alcanzado"));

        ApiResponse<TransaccionResponse> res = ejecutar(recarga());

        assertThat(res.getStatus()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(res.getDatos().getEstatus()).isEqualTo(EstatusTransaccion.PENDIENTE_CONFIRMACION);
        assertThat(res.getDatos().getMensajeGestoPago()).isNull();
        verify(registro).marcarPorConfirmar(eq(99), any());
        verify(registro, never()).rechazar(any(), any());
    }

    @Test
    @DisplayName("Sin respuesta (timeout de red): 202 y queda por confirmar")
    void sinRespuesta() {
        registroCierraCon(EstatusTransaccion.PENDIENTE_CONFIRMACION);
        when(gateway.enviar(anyString())).thenReturn(new Resultado(null, null, "Read timed out", true));

        assertThat(ejecutar(recarga()).getStatus()).isEqualTo(HttpStatus.ACCEPTED);
        verify(registro).marcarPorConfirmar(eq(99), any());
    }

    @Test
    @DisplayName("No se pudo enviar (sin token): 503 y saldo devuelto")
    void noEnviada() {
        registroCierraCon(EstatusTransaccion.RECHAZADA);
        when(gateway.enviar(anyString())).thenReturn(new Resultado(null, null, "No hay token vigente", false));

        assertThat(ejecutar(recarga()).getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        verify(registro).rechazar(eq(99), any());
    }
}
