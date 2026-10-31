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
 *   <li>Otro codigo o sin respuesta: se reintenta en la siguiente ejecucion.</li>
 * </ul>
 * Las que pasan de 24 h se quedan en PENDIENTE_CONFIRMACION para revision manual (el saldo sigue apartado).
 */
@Component
@Slf4j
public class ConfirmTxProceso {

    static final String CODIGO_APLICADA = "06";
    static final String CODIGO_NO_APLICADA = "70";
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
        List<Transaccion> candidatas = transaccionRepository.findByEstatusInAndFechaCreacionBetweenOrderByFechaCreacion(
                EnumSet.of(EstatusTransaccion.PENDIENTE, EstatusTransaccion.PENDIENTE_CONFIRMACION),
                ahora.minusHours(24), ahora.minusSeconds(SEGUNDOS_ESPERA_MINIMA));
        for (Transaccion tx : candidatas) {
            boolean huerfana = tx.getEstatus() == EstatusTransaccion.PENDIENTE
                    && tx.getFechaCreacion().isAfter(ahora.minusMinutes(MINUTOS_PENDIENTE_HUERFANA));
            if (!huerfana) {
                confirmar(tx);
            }
        }
    }

    void confirmar(Transaccion tx) {
        Resultado resultado = gateway.confirmar(tx.getPayloadEnviado());
        if (!resultado.tieneRespuesta()) {
            log.warn("confirmTx sin respuesta para transaccion ID={}: {}", tx.getId(), resultado.error());
            return;
        }
        if (CODIGO_APLICADA.equals(resultado.codigo())) {
            registro.aplicar(tx.getId(), resultado);
        } else if (CODIGO_NO_APLICADA.equals(resultado.codigo())) {
            registro.rechazar(tx.getId(), resultado);
        } else {
            log.warn("confirmTx codigo {} para transaccion ID={}; se reintentara", resultado.codigo(), tx.getId());
        }
    }
}
