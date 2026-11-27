package com.proyecto.servicios.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Cupo diario de llamadas a GestoPago por operacion. El contador vive en la base de datos y se
 * incrementa con una sola sentencia atomica, asi que reinicios, redespliegues o varias instancias no
 * pueden rebasarlo.
 */
@Component
@Slf4j
public class LimiteLlamadasGestoPago {

    /** Inserta el primer conteo del dia o suma uno, solo si aun no se llega al maximo. */
    private static final String RESERVAR = """
            INSERT INTO llamadas_gestopago (fecha, operacion, total) VALUES (?, ?, 1)
            ON CONFLICT (fecha, operacion)
            DO UPDATE SET total = llamadas_gestopago.total + 1
            WHERE llamadas_gestopago.total < ?""";

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public LimiteLlamadasGestoPago(JdbcTemplate jdbc, Clock clock,
                                   @Value("${gestopago.tx.zona-horaria}") String zonaHoraria) {
        this.jdbc = jdbc;
        // El dia se cuenta en la zona horaria del comercio
        this.clock = clock.withZone(ZoneId.of(zonaHoraria));
    }

    /** @return true si la llamada cabe en el cupo de hoy (y ya quedo contada); false si se agoto */
    public boolean reservar(String operacion, int maximoDiario) {
        if (maximoDiario < 1) {
            return false;
        }
        int filas = jdbc.update(RESERVAR, LocalDate.now(clock), operacion, maximoDiario);
        if (filas == 0) {
            log.warn("Cupo diario de GestoPago agotado para {} (maximo {} al dia); no se llama", operacion, maximoDiario);
            return false;
        }
        return true;
    }
}
