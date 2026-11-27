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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ConfirmTxProceso - resolución de transacciones sin respuesta con reintentos espaciados")
class ConfirmTxProcesoTest {

    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-10-23T18:00:00Z"), ZoneOffset.UTC);
    private static final LocalDateTime AHORA = LocalDateTime.now(RELOJ);

    @Mock private TransaccionRepository transaccionRepository;
    @Mock private RegistroTransacciones registro;
    @Mock private GestoPagoTxGateway gateway;

    private ConfirmTxProceso proceso;

    @BeforeEach
    void setUp() {
        proceso = new ConfirmTxProceso(transaccionRepository, registro, gateway, RELOJ);
    }

    private static Transaccion transaccion(int id, EstatusTransaccion estatus, int minutosAtras, int intentos) {
        Transaccion tx = new Transaccion();
        tx.setId(id);
        tx.setEstatus(estatus);
        tx.setPayloadEnviado("{\"upc\":\"UPC" + id + "\"}");
        tx.setFechaCreacion(AHORA.minusMinutes(minutosAtras));
        tx.setIntentosConfirmacion(intentos);
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
        when(transaccionRepository.buscarPorConfirmar(any(), any(), any(), any())).thenReturn(List.of(txs));
    }

    @Test
    @DisplayName("Solo busca las que ya tienen más de 62 s, menos de 24 h y cuyo reintento ya toca")
    void ventanaDeBusqueda() {
        pendientes();

        proceso.confirmarPendientes();

        verify(transaccionRepository).buscarPorConfirmar(any(), eq(AHORA.minusHours(24)), eq(AHORA.minusSeconds(62)), eq(AHORA));
    }

    @Test
    @DisplayName("Código 06: la marca como aplicada; código 70: devuelve el saldo")
    void aplicadaYNoAplicada() {
        pendientes(transaccion(1, EstatusTransaccion.PENDIENTE_CONFIRMACION, 2, 0),
                transaccion(2, EstatusTransaccion.PENDIENTE_CONFIRMACION, 2, 0));
        when(gateway.confirmar("{\"upc\":\"UPC1\"}")).thenReturn(codigo("06"));
        when(gateway.confirmar("{\"upc\":\"UPC2\"}")).thenReturn(codigo("70"));

        proceso.confirmarPendientes();

        verify(registro).aplicar(eq(1), any());
        verify(registro).rechazar(eq(2), any());
    }

    @Test
    @DisplayName("Sin resultado: programa el siguiente intento con espera creciente (1, 5, 15, 60 min)")
    void sinResultado_esperaCreciente() {
        pendientes(transaccion(1, EstatusTransaccion.PENDIENTE_CONFIRMACION, 30, 0),
                transaccion(2, EstatusTransaccion.PENDIENTE_CONFIRMACION, 30, 1),
                transaccion(3, EstatusTransaccion.PENDIENTE_CONFIRMACION, 30, 2),
                transaccion(4, EstatusTransaccion.PENDIENTE_CONFIRMACION, 30, 6));
        when(gateway.confirmar(anyString())).thenReturn(new Resultado(null, null, "timeout", true));

        proceso.confirmarPendientes();

        verify(registro).programarSiguienteConfirmacion(eq(1), any(), eq(AHORA.plusMinutes(1)), eq(ConfirmTxProceso.MAX_INTENTOS));
        verify(registro).programarSiguienteConfirmacion(eq(2), any(), eq(AHORA.plusMinutes(5)), eq(ConfirmTxProceso.MAX_INTENTOS));
        verify(registro).programarSiguienteConfirmacion(eq(3), any(), eq(AHORA.plusMinutes(15)), eq(ConfirmTxProceso.MAX_INTENTOS));
        verify(registro).programarSiguienteConfirmacion(eq(4), any(), eq(AHORA.plusMinutes(60)), eq(ConfirmTxProceso.MAX_INTENTOS));
        verify(registro, never()).aplicar(any(), any());
        verify(registro, never()).rechazar(any(), any());
    }

    @Test
    @DisplayName("Otro código: tampoco cierra la transacción, programa reintento")
    void otroCodigo() {
        pendientes(transaccion(1, EstatusTransaccion.PENDIENTE_CONFIRMACION, 2, 0));
        when(gateway.confirmar(anyString())).thenReturn(codigo("99"));

        proceso.confirmarPendientes();

        verify(registro).programarSiguienteConfirmacion(eq(1), any(), any(), eq(ConfirmTxProceso.MAX_INTENTOS));
    }

    @Test
    @DisplayName("Si no se pudo llamar (pausa general o sin token) no cuenta el intento ni sigue con las demás")
    void noEnviada_cortaLaRonda() {
        pendientes(transaccion(1, EstatusTransaccion.PENDIENTE_CONFIRMACION, 2, 0),
                transaccion(2, EstatusTransaccion.PENDIENTE_CONFIRMACION, 2, 0));
        when(gateway.confirmar("{\"upc\":\"UPC1\"}")).thenReturn(new Resultado(null, null, "en pausa", false));

        proceso.confirmarPendientes();

        verify(gateway, times(1)).confirmar(anyString());
        verify(registro, never()).programarSiguienteConfirmacion(any(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("Una PENDIENTE reciente puede seguir en curso: no se confirma todavía")
    void pendienteRecienteNoSeToca() {
        pendientes(transaccion(1, EstatusTransaccion.PENDIENTE, 2, 0));

        proceso.confirmarPendientes();

        verify(gateway, never()).confirmar(anyString());
    }

    @Test
    @DisplayName("Una PENDIENTE de más de 5 minutos (ejecución interrumpida) sí se confirma")
    void pendienteHuerfanaSeConfirma() {
        pendientes(transaccion(1, EstatusTransaccion.PENDIENTE, 10, 0));
        when(gateway.confirmar(anyString())).thenReturn(codigo("70"));

        proceso.confirmarPendientes();

        verify(registro).rechazar(eq(1), any());
    }
}
