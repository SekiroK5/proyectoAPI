package com.proyecto.servicios.service.impl;

import com.proyecto.servicios.client.GestoPagoTxGateway.Resultado;
import com.proyecto.servicios.entity.sf.Cuenta;
import com.proyecto.servicios.entity.sf.Transaccion;
import com.proyecto.servicios.enums.Catalogos.EstatusTransaccion;
import com.proyecto.servicios.model.gestopago.SendTxResponseXml;
import com.proyecto.servicios.repository.sf.CuentaRepository;
import com.proyecto.servicios.repository.sf.TransaccionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Movimientos de saldo de los pagos con GestoPago. Cada metodo es una transaccion corta de base de datos
 * con la cuenta bloqueada (SELECT ... FOR UPDATE); la llamada a GestoPago ocurre entre ellas, fuera de
 * cualquier transaccion, porque puede tardar hasta 62 s.
 * <p>
 * El saldo se aparta antes de enviar y solo se devuelve cuando GestoPago confirma que la operacion
 * no se aplico; si no hay respuesta queda apartado hasta resolverlo con confirmTx.
 */
@Service
@Slf4j
public class RegistroTransacciones {

    private static final int MAX_TEXTO = 500;
    private static final int MAX_PIN = 250;

    private final CuentaRepository cuentaRepository;
    private final TransaccionRepository transaccionRepository;

    public RegistroTransacciones(CuentaRepository cuentaRepository, TransaccionRepository transaccionRepository) {
        this.cuentaRepository = cuentaRepository;
        this.transaccionRepository = transaccionRepository;
    }

    /**
     * Vuelve a validar el saldo con la cuenta bloqueada, lo descuenta y registra la transaccion PENDIENTE.
     *
     * @return la transaccion registrada, o vacio si el saldo ya no alcanza (otro pago simultaneo lo consumio)
     */
    @Transactional("sfTransactionManager")
    public Optional<Transaccion> apartarSaldo(Integer cuentaId, Transaccion nueva) {
        Cuenta cuenta = cuentaRepository.bloquearPorId(cuentaId)
                .orElseThrow(() -> new IllegalStateException("No existe la cuenta " + cuentaId));
        if (cuenta.getSaldo().compareTo(nueva.getMonto()) < 0) {
            return Optional.empty();
        }
        cuenta.setSaldo(cuenta.getSaldo().subtract(nueva.getMonto()));
        nueva.setCuenta(cuenta);
        nueva.setEstatus(EstatusTransaccion.PENDIENTE);
        Transaccion guardada = transaccionRepository.save(nueva);
        log.info("Saldo apartado: transaccion ID={} UPC={} monto={}", guardada.getId(), guardada.getUpc(), guardada.getMonto());
        return Optional.of(guardada);
    }

    /** GestoPago aplico la operacion: el cargo queda firme. */
    @Transactional("sfTransactionManager")
    public Transaccion aplicar(Integer transaccionId, Resultado resultado) {
        Transaccion tx = bloquear(transaccionId);
        if (tx.getEstatus() == EstatusTransaccion.APLICADA) {
            return tx;
        }
        guardarRespuesta(tx, resultado);
        tx.setEstatus(EstatusTransaccion.APLICADA);
        log.info("Transaccion ID={} APLICADA autorizacion={}", tx.getId(), tx.getNumAutorizacion());
        return transaccionRepository.save(tx);
    }

    /** GestoPago no aplico la operacion (o nunca se envio): se devuelve el saldo apartado. */
    @Transactional("sfTransactionManager")
    public Transaccion rechazar(Integer transaccionId, Resultado resultado) {
        Transaccion tx = bloquear(transaccionId);
        if (tx.getEstatus() == EstatusTransaccion.RECHAZADA || tx.getEstatus() == EstatusTransaccion.APLICADA) {
            // Nunca devolver dos veces ni revertir una operacion ya aplicada
            return tx;
        }
        Cuenta cuenta = cuentaRepository.bloquearPorId(tx.getCuenta().getId())
                .orElseThrow(() -> new IllegalStateException("No existe la cuenta de la transaccion " + transaccionId));
        cuenta.setSaldo(cuenta.getSaldo().add(tx.getMonto()));
        guardarRespuesta(tx, resultado);
        tx.setEstatus(EstatusTransaccion.RECHAZADA);
        log.info("Transaccion ID={} RECHAZADA codigo={}; saldo devuelto", tx.getId(), tx.getCodigoRespuesta());
        return transaccionRepository.save(tx);
    }

    /** Sin respuesta de GestoPago: el saldo sigue apartado hasta confirmarla con confirmTx. */
    @Transactional("sfTransactionManager")
    public Transaccion marcarPorConfirmar(Integer transaccionId, Resultado resultado) {
        Transaccion tx = bloquear(transaccionId);
        if (tx.getEstatus() != EstatusTransaccion.PENDIENTE) {
            return tx;
        }
        guardarRespuesta(tx, resultado);
        tx.setEstatus(EstatusTransaccion.PENDIENTE_CONFIRMACION);
        log.warn("Transaccion ID={} PENDIENTE_CONFIRMACION: {}", tx.getId(), resultado.error());
        return transaccionRepository.save(tx);
    }

    /**
     * confirmTx no resolvio la transaccion: cuenta el intento y programa el siguiente. Al llegar al maximo
     * pasa a REVISION_MANUAL (el saldo sigue apartado) y ya no se vuelve a llamar a GestoPago por ella.
     */
    @Transactional("sfTransactionManager")
    public Transaccion programarSiguienteConfirmacion(Integer transaccionId, Resultado resultado,
                                                      LocalDateTime proxima, int maxIntentos) {
        Transaccion tx = bloquear(transaccionId);
        if (tx.getEstatus() == EstatusTransaccion.APLICADA || tx.getEstatus() == EstatusTransaccion.RECHAZADA) {
            return tx;
        }
        guardarRespuesta(tx, resultado);
        int intentos = (tx.getIntentosConfirmacion() == null ? 0 : tx.getIntentosConfirmacion()) + 1;
        tx.setIntentosConfirmacion(intentos);
        if (intentos >= maxIntentos) {
            tx.setEstatus(EstatusTransaccion.REVISION_MANUAL);
            tx.setProximaConfirmacion(null);
            log.warn("Transaccion ID={} pasa a REVISION_MANUAL tras {} intentos de confirmTx; saldo apartado", tx.getId(), intentos);
        } else {
            tx.setEstatus(EstatusTransaccion.PENDIENTE_CONFIRMACION);
            tx.setProximaConfirmacion(proxima);
            log.info("Transaccion ID={} sin resolver (intento {}/{}); siguiente confirmTx a las {}", tx.getId(), intentos, maxIntentos, proxima);
        }
        return transaccionRepository.save(tx);
    }

    private Transaccion bloquear(Integer transaccionId) {
        return transaccionRepository.bloquearPorId(transaccionId)
                .orElseThrow(() -> new IllegalStateException("No existe la transaccion " + transaccionId));
    }

    /** Bitacora obligatoria: se guarda la respuesta tal como llego. */
    private static void guardarRespuesta(Transaccion tx, Resultado resultado) {
        if (resultado.cuerpo() != null) {
            tx.setRespuesta(resultado.cuerpo());
        }
        SendTxResponseXml xml = resultado.xml();
        if (xml == null) {
            tx.setTextoRespuesta(recortar(resultado.error(), MAX_TEXTO));
            return;
        }
        tx.setCodigoRespuesta(recortar(xml.codigo(), 4));
        tx.setTextoRespuesta(recortar(xml.texto(), MAX_TEXTO));
        tx.setIdTx(recortar(xml.idTransaccion(), 30));
        tx.setNumAutorizacion(recortar(xml.getNumAutorizacion(), 60));
        if (xml.getMensaje() != null) {
            tx.setPin(recortar(xml.getMensaje().getPin(), MAX_PIN));
        }
    }

    private static String recortar(String valor, int maximo) {
        if (valor == null) {
            return null;
        }
        String limpio = valor.trim();
        return limpio.length() > maximo ? limpio.substring(0, maximo) : limpio;
    }
}
