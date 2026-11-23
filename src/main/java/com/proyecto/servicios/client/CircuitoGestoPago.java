package com.proyecto.servicios.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Pausa general (circuit breaker): si GestoPago falla varias veces seguidas (timeout, error HTTP,
 * respuesta ilegible o token rechazado), se deja de llamarlo durante un rato en lugar de insistir.
 * Insistir contra un servicio caido o que ya nos limito es justo lo que hace que bloqueen la IP.
 * <p>
 * Al terminar la pausa se permite un intento: si falla, se vuelve a pausar de inmediato; si sale
 * bien, el contador se reinicia. Un rechazo de negocio (codigo distinto de 01) no cuenta como falla.
 */
@Component
@Slf4j
public class CircuitoGestoPago {

    private final Clock clock;
    private final int fallasParaPausar;
    private final Duration pausa;

    private int fallasSeguidas;
    private Instant pausadoHasta;

    public CircuitoGestoPago(Clock clock,
                             @Value("${gestopago.circuito.fallas-para-pausar:5}") int fallasParaPausar,
                             @Value("${gestopago.circuito.pausa-minutos:10}") long pausaMinutos) {
        this.clock = clock;
        this.fallasParaPausar = fallasParaPausar;
        this.pausa = Duration.ofMinutes(pausaMinutos);
    }

    /** true si se puede llamar a GestoPago en este momento. */
    public synchronized boolean permite() {
        return pausadoHasta == null || !clock.instant().isBefore(pausadoHasta);
    }

    public synchronized void registrarExito() {
        fallasSeguidas = 0;
        pausadoHasta = null;
    }

    public synchronized void registrarFalla(String operacion) {
        fallasSeguidas++;
        if (fallasSeguidas >= fallasParaPausar) {
            pausadoHasta = clock.instant().plus(pausa);
            // Tras la pausa basta una falla mas para volver a pausar
            fallasSeguidas = fallasParaPausar - 1;
            log.warn("GestoPago fallo {} veces seguidas (ultima: {}). Se pausan las llamadas hasta {}",
                    fallasParaPausar, operacion, pausadoHasta);
        }
    }
}
