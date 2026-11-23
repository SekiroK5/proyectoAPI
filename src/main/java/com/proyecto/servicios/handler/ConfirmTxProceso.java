package com.proyecto.servicios.handler;

import com.proyecto.servicios.client.GestoPagoTxGateway;
import com.proyecto.servicios.client.GestoPagoTxGateway.Resultado;
import com.proyecto.servicios.entity.sf.Transaccion;
import com.proyecto.servicios.enums.Catalogos.EstatusTransaccion;
import com.proyecto.servicios.repository.sf.TransaccionRepository;
import com.proyecto.servicios.service.impl.RegistroTransacciones;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;

/**
 * Resuelve las transacciones sin respuesta con confirmTx, como exige la documentacion de PuntoRed:
 * mismos parametros que sendTx, no antes de 62 s y no despues de 24 h.
 * <ul>
 *   <li>Codigo 06: se aplico. El cargo queda firme.</li>
 *   <li>Codigo 70: no se aplico. Se devuelve el saldo apartado.</li>
 *   <li>Otro codigo o sin respuesta: se reintenta con esperas crecientes (1, 5, 15 y 60 min) y como maximo
 *       {@value #MAX_INTENTOS} veces; despues pasa a REVISION_MANUAL con el saldo apartado.</li>
 * </ul>
 * Antes se reintentaba cada minuto durante 24 h (hasta 1,440 llamadas por transaccion).
 */
@Component
@Slf4j
public class ConfirmTxProceso {

    static final String CODIGO_APLICADA = "06";
    static final String CODIGO_NO_APLICADA = "70";
    static final int MAX_INTENTOS = 8;
    /** Espera antes del siguiente intento, segun cuantos van; despues del ultimo valor se repite 60 min. */
    private static final int[] ESPERA_MINUTOS = {1, 5, 15, 60};
    /** Las PENDIENTE de mas de 5 min son de una ejecucion que se interrumpio antes de registrar la respuesta. */
    private static final long MINUTOS_PENDIENTE_HUERFANA = 5;
    private static final long SEGUNDOS_ESPERA_MINIMA = 62;

    private final TransaccionRepository transaccionRepository;
    private final RegistroTransacciones registro;
    private final GestoPagoTxGateway gateway;
    private final Clock clock;

    public ConfirmTxProceso(TransaccionRepository transaccionRepository,
                            RegistroTransacciones registro,
                            GestoPagoTxGateway gateway,
                            Clock clock) {
        this.transaccionRepository = transaccionRepository;
        this.registro = registro;
        this.gateway = gateway;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${gestopago.tx.confirmacion-ms:60000}", initialDelayString = "${gestopago.tx.confirmacion-ms:60000}")
    public void confirmarPendientes() {
        LocalDateTime ahora = LocalDateTime.now(clock);
        List<Transaccion> candidatas = transaccionRepository.buscarPorConfirmar(
                EnumSet.of(EstatusTransaccion.PENDIENTE, EstatusTransaccion.PENDIENTE_CONFIRMACION),
                ahora.minusHours(24), ahora.minusSeconds(SEGUNDOS_ESPERA_MINIMA), ahora);
        for (Transaccion tx : candidatas) {
            boolean enCurso = tx.getEstatus() == EstatusTransaccion.PENDIENTE
                    && tx.getFechaCreacion().isAfter(ahora.minusMinutes(MINUTOS_PENDIENTE_HUERFANA));
            if (!enCurso && !confirmar(tx, ahora)) {
                // No se pudo llamar (pausa general o sin token): no se insiste con las demas en esta ronda
                return;
            }
        }
    }

    /** @return false si no se llego a llamar a GestoPago */
    boolean confirmar(Transaccion tx, LocalDateTime ahora) {
        Resultado resultado = gateway.confirmar(tx.getPayloadEnviado());
        if (!resultado.enviada()) {
            log.warn("confirmTx no enviado para transaccion ID={}: {}", tx.getId(), resultado.error());
            return false;
        }
        if (resultado.tieneRespuesta() && CODIGO_APLICADA.equals(resultado.codigo())) {
            registro.aplicar(tx.getId(), resultado);
        } else if (resultado.tieneRespuesta() && CODIGO_NO_APLICADA.equals(resultado.codigo())) {
            registro.rechazar(tx.getId(), resultado);
        } else {
            int intentoActual = tx.getIntentosConfirmacion() == null ? 0 : tx.getIntentosConfirmacion();
            int espera = ESPERA_MINUTOS[Math.min(intentoActual, ESPERA_MINUTOS.length - 1)];
            log.warn("confirmTx sin resultado definitivo para transaccion ID={} (codigo {})", tx.getId(), resultado.codigo());
            registro.programarSiguienteConfirmacion(tx.getId(), resultado, ahora.plusMinutes(espera), MAX_INTENTOS);
        }
        return true;
    }
}
