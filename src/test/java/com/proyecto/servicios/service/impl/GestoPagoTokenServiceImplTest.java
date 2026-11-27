package com.proyecto.servicios.service.impl;

import com.proyecto.servicios.client.CircuitoGestoPago;
import com.proyecto.servicios.client.GestoPagoAuthClient;
import com.proyecto.servicios.entity.gestopago.GestoPagoToken;
import com.proyecto.servicios.mapper.GestoPagoTokenMapper;
import com.proyecto.servicios.model.gestopago.GestoPagoAuthResponse;
import com.proyecto.servicios.repository.gestopago.GestoPagoTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("GestoPagoTokenServiceImpl - como máximo una renovación de token cada 15 minutos")
class GestoPagoTokenServiceImplTest {

    static class RelojManual extends Clock {
        private Instant ahora = Instant.parse("2026-11-23T12:00:00Z");
        void adelantar(Duration d) { ahora = ahora.plus(d); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return ahora; }
    }

    @Mock private GestoPagoAuthClient authClient;
    @Mock private GestoPagoTokenRepository tokenRepository;
    @Mock private GestoPagoTokenMapper tokenMapper;

    private final RelojManual reloj = new RelojManual();
    private CircuitoGestoPago circuito;
    private GestoPagoTokenServiceImpl servicio;

    @BeforeEach
    void setUp() {
        circuito = new CircuitoGestoPago(reloj, 5, 10);
        servicio = new GestoPagoTokenServiceImpl(authClient, tokenRepository, tokenMapper, reloj, circuito, 15);
        ReflectionTestUtils.setField(servicio, "idDistribuidor", 83);
        ReflectionTestUtils.setField(servicio, "codigoDispositivo", "GPS83-TPV-17");
        ReflectionTestUtils.setField(servicio, "password", "clave-de-prueba");
        lenient().when(tokenRepository.findByIdDistribuidorAndCodigoDispositivo(anyInt(), anyString()))
                .thenReturn(Optional.of(new GestoPagoToken()));
    }

    private static GestoPagoAuthResponse conToken() {
        GestoPagoAuthResponse r = new GestoPagoAuthResponse();
        r.setToken("token-nuevo");
        return r;
    }

    @Test
    @DisplayName("Renueva y guarda el token")
    void renueva() {
        when(authClient.authenticate(anyInt(), anyString(), anyString())).thenReturn(conToken());

        assertThat(servicio.renovarToken()).isTrue();
        verify(tokenRepository).save(any(GestoPagoToken.class));
    }

    @Test
    @DisplayName("Un segundo intento antes de 15 minutos no llama a /authenticate")
    void noRenuevaAntesDe15Minutos() {
        when(authClient.authenticate(anyInt(), anyString(), anyString())).thenReturn(conToken());
        servicio.renovarToken();

        reloj.adelantar(Duration.ofMinutes(14));
        assertThat(servicio.renovarToken()).isFalse();

        reloj.adelantar(Duration.ofMinutes(1));
        assertThat(servicio.renovarToken()).isTrue();
        verify(authClient, times(2)).authenticate(anyInt(), anyString(), anyString());
    }

    @Test
    @DisplayName("Aunque falle, tampoco reintenta antes de 15 minutos")
    void fallaTambienRespetaElIntervalo() {
        when(authClient.authenticate(anyInt(), anyString(), anyString())).thenThrow(new RuntimeException("caido"));

        assertThat(servicio.renovarToken()).isFalse();
        assertThat(servicio.renovarToken()).isFalse();
        assertThat(servicio.renovarToken()).isFalse();

        verify(authClient, times(1)).authenticate(anyInt(), anyString(), anyString());
    }

    @Test
    @DisplayName("Con las llamadas en pausa no llama a /authenticate")
    void enPausaNoLlama() {
        for (int i = 0; i < 5; i++) {
            circuito.registrarFalla("x");
        }

        assertThat(servicio.renovarToken()).isFalse();
        verify(authClient, never()).authenticate(anyInt(), anyString(), anyString());
    }
}
