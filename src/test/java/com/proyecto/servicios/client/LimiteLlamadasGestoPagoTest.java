package com.proyecto.servicios.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Date;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("LimiteLlamadasGestoPago - cupo diario en la base de datos")
class LimiteLlamadasGestoPagoTest {

    /** 2026-11-24 03:00 UTC = 2026-11-23 21:00 en Ciudad de México: el día se cuenta en hora local. */
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-11-24T03:00:00Z"), ZoneOffset.UTC);

    @Mock
    private JdbcTemplate jdbc;

    private LimiteLlamadasGestoPago limite() {
        return new LimiteLlamadasGestoPago(jdbc, RELOJ, "America/Mexico_City");
    }

    @Test
    @DisplayName("Si el UPSERT condicional actualiza una fila, la llamada cabe en el cupo")
    void dentroDelCupo() {
        when(jdbc.update(anyString(), eq(Date.valueOf(LocalDate.of(2026, 11, 23))), eq("getProductList"), eq(2))).thenReturn(1);

        assertThat(limite().reservar("getProductList", 2)).isTrue();
        verify(jdbc).update(contains("ON CONFLICT (fecha, operacion)"),
                eq(Date.valueOf(LocalDate.of(2026, 11, 23))), eq("getProductList"), eq(2));
    }

    @Test
    @DisplayName("Si no actualiza ninguna fila, el cupo del día ya se agotó")
    void cupoAgotado() {
        when(jdbc.update(anyString(), eq(Date.valueOf(LocalDate.of(2026, 11, 23))), eq("getProductList"), eq(2))).thenReturn(0);

        assertThat(limite().reservar("getProductList", 2)).isFalse();
    }

    @Test
    @DisplayName("Un máximo de cero no permite ninguna llamada ni toca la base")
    void maximoCero() {
        assertThat(limite().reservar("getProductList", 0)).isFalse();
        verifyNoInteractions(jdbc);
    }
}
