package com.proyecto.servicios.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CircuitoGestoPago - pausa general tras fallas consecutivas")
class CircuitoGestoPagoTest {

    /** Reloj que se puede adelantar a mano. */
    static class RelojManual extends Clock {
        private Instant ahora = Instant.parse("2026-11-23T12:00:00Z");

        void adelantar(Duration d) {
            ahora = ahora.plus(d);
        }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return ahora; }
    }

    private final RelojManual reloj = new RelojManual();
    private final CircuitoGestoPago circuito = new CircuitoGestoPago(reloj, 3, 10);

    @Test
    @DisplayName("Permite llamar mientras no se llegue al número de fallas seguidas")
    void permiteAntesDelUmbral() {
        circuito.registrarFalla("x");
        circuito.registrarFalla("x");

        assertThat(circuito.permite()).isTrue();
    }

    @Test
    @DisplayName("Al llegar al umbral pausa las llamadas durante el tiempo configurado")
    void pausaAlUmbral() {
        for (int i = 0; i < 3; i++) {
            circuito.registrarFalla("x");
        }

        assertThat(circuito.permite()).isFalse();
        reloj.adelantar(Duration.ofMinutes(9));
        assertThat(circuito.permite()).isFalse();
        reloj.adelantar(Duration.ofMinutes(1));
        assertThat(circuito.permite()).isTrue();
    }

    @Test
    @DisplayName("Tras la pausa, una sola falla vuelve a pausar")
    void unaFallaTrasLaPausaVuelveAPausar() {
        for (int i = 0; i < 3; i++) {
            circuito.registrarFalla("x");
        }
        reloj.adelantar(Duration.ofMinutes(10));

        circuito.registrarFalla("x");

        assertThat(circuito.permite()).isFalse();
    }

    @Test
    @DisplayName("Un éxito reinicia el conteo de fallas")
    void exitoReinicia() {
        circuito.registrarFalla("x");
        circuito.registrarFalla("x");
        circuito.registrarExito();
        circuito.registrarFalla("x");
        circuito.registrarFalla("x");

        assertThat(circuito.permite()).isTrue();
    }
}
