package com.proyecto.servicios.handler;

import com.proyecto.servicios.client.GestoPagoTxGateway;
import com.proyecto.servicios.client.GestoPagoTxGateway.Resultado;
import com.proyecto.servicios.entity.sf.Transaccion;
import com.proyecto.servicios.enums.Catalogos.EstatusTransaccion;
import com.proyecto.servicios.model.gestopago.SendTxResponseXml;
import com.proyecto.servicios.repository.sf.TransaccionRepository;
import com.proyecto.servicios.service.impl.RegistroTransacciones;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ConfirmTxProceso - resolución de transacciones sin respuesta")
class ConfirmTxProcesoTest {

    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-10-23T18:00:00Z"), ZoneOffset.UTC);

    @Mock private TransaccionRepository transaccionRepository;
    @Mock private RegistroTransacciones registro;
    @Mock private GestoPagoTxGateway gateway;

    private ConfirmTxProceso proceso;

    @BeforeEach
    void setUp() {
        proceso = new ConfirmTxProceso(transaccionRepository, registro, gateway, RELOJ);
    }

    private static Transaccion transaccion(int id, EstatusTransaccion estatus, int minutosAtras) {
        Transaccion tx = new Transaccion();
        tx.setId(id);
        tx.setEstatus(estatus);
        tx.setPayloadEnviado("{\"upc\":\"UPC" + id + "\"}");
        tx.setFechaCreacion(LocalDateTime.now(RELOJ).minusMinutes(minutosAtras));
        return tx;
    }

    private static Resultado codigo(String codigo) {
        SendTxResponseXml xml = new SendTxResponseXml();
        SendTxResponseXml.Mensaje mensaje = new SendTxResponseXml.Mensaje();
        mensaje.setCodigo(codigo);
        xml.setMensaje(mensaje);
        return new Resultado(xml, "<RESPONSE/>", null, true);
    }

    private void pendientes(Transaccion... txs) {
        when(transaccionRepository.findByEstatusInAndFechaCreacionBetweenOrderByFechaCreacion(any(), any(), any()))
                .thenReturn(List.of(txs));
    }

    @Test
    @DisplayName("Código 06: la marca como aplicada; código 70: devuelve el saldo")
    void aplicadaYNoAplicada() {
        pendientes(transaccion(1, EstatusTransaccion.PENDIENTE_CONFIRMACION, 2),
                transaccion(2, EstatusTransaccion.PENDIENTE_CONFIRMACION, 2));
        when(gateway.confirmar("{\"upc\":\"UPC1\"}")).thenReturn(codigo("06"));
        when(gateway.confirmar("{\"upc\":\"UPC2\"}")).thenReturn(codigo("70"));

        proceso.confirmarPendientes();

        verify(registro).aplicar(eq(1), any());
        verify(registro).rechazar(eq(2), any());
    }

    @Test
    @DisplayName("Sin respuesta u otro código: no la cierra, se reintenta después")
    void sinResultadoSeReintenta() {
        pendientes(transaccion(1, EstatusTransaccion.PENDIENTE_CONFIRMACION, 2),
                transaccion(2, EstatusTransaccion.PENDIENTE_CONFIRMACION, 2));
        when(gateway.confirmar("{\"upc\":\"UPC1\"}")).thenReturn(new Resultado(null, null, "timeout", true));
        when(gateway.confirmar("{\"upc\":\"UPC2\"}")).thenReturn(codigo("99"));

        proceso.confirmarPendientes();

        verify(registro, never()).aplicar(any(), any());
        verify(registro, never()).rechazar(any(), any());
    }

    @Test
    @DisplayName("Una PENDIENTE reciente puede seguir en curso: no se confirma todavía")
    void pendienteRecienteNoSeToca() {
        pendientes(transaccion(1, EstatusTransaccion.PENDIENTE, 2));

        proceso.confirmarPendientes();

        verify(gateway, never()).confirmar(anyString());
    }

    @Test
    @DisplayName("Una PENDIENTE de más de 5 minutos (ejecución interrumpida) sí se confirma")
    void pendienteHuerfanaSeConfirma() {
        pendientes(transaccion(1, EstatusTransaccion.PENDIENTE, 10));
        when(gateway.confirmar(anyString())).thenReturn(codigo("70"));

        proceso.confirmarPendientes();

        verify(registro).rechazar(eq(1), any());
    }
}
