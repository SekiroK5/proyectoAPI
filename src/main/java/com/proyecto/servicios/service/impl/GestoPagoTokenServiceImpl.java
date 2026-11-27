package com.proyecto.servicios.service.impl;

import com.proyecto.servicios.client.CircuitoGestoPago;
import com.proyecto.servicios.client.GestoPagoAuthClient;
import com.proyecto.servicios.entity.gestopago.GestoPagoToken;
import com.proyecto.servicios.mapper.GestoPagoTokenMapper;
import com.proyecto.servicios.model.gestopago.GestoPagoAuthResponse;
import com.proyecto.servicios.repository.gestopago.GestoPagoTokenRepository;
import com.proyecto.servicios.service.GestoPagoTokenService;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@Slf4j
public class GestoPagoTokenServiceImpl implements GestoPagoTokenService {

    private final GestoPagoAuthClient gestoPagoAuthClient;
    private final GestoPagoTokenRepository tokenRepository;
    private final GestoPagoTokenMapper tokenMapper;

    @Value("${gestopago.auth.id-distribuidor}")
    private Integer idDistribuidor;

    @Value("${gestopago.auth.codigo-dispositivo}")
    private String codigoDispositivo;

    @Value("${gestopago.auth.password}")
    private String password;

    /** El token dura 24 h; se renueva un poco antes para no usarlo justo cuando vence. */
    @Value("${gestopago.auth.vigencia-horas:23}")
    private long vigenciaHoras;

    private final Clock clock;
    private final CircuitoGestoPago circuito;
    /**
     * Minimo entre dos intentos de renovacion. PuntoRed no permite pedir un token por peticion: aunque
     * fallen muchas peticiones seguidas (401/403, sin token), solo se llama a /authenticate una vez en este lapso.
     */
    private final Duration intervaloMinimo;
    private Instant ultimoIntento;

    public GestoPagoTokenServiceImpl(GestoPagoAuthClient gestoPagoAuthClient,
                                     GestoPagoTokenRepository tokenRepository,
                                     GestoPagoTokenMapper tokenMapper,
                                     Clock clock,
                                     CircuitoGestoPago circuito,
                                     @Value("${gestopago.auth.intervalo-minimo-minutos:15}") long intervaloMinimoMinutos) {
        this.gestoPagoAuthClient = gestoPagoAuthClient;
        this.tokenRepository = tokenRepository;
        this.tokenMapper = tokenMapper;
        this.clock = clock;
        this.circuito = circuito;
        this.intervaloMinimo = Duration.ofMinutes(intervaloMinimoMinutos);
    }

    /**
     * Revisa cada hora (y al arrancar) si el token vencio. Antes se renovaba en cada ejecucion,
     * lo que pedia 24 tokens al dia; la documentacion de PuntoRed solo permite uno.
     */
    @Override
    @Scheduled(fixedRateString = "${gestopago.auth.refresh-rate-ms:3600000}", initialDelay = 0)
    public void renovarSiVence() {
        Optional<GestoPagoToken> actual = tokenRepository.findByIdDistribuidorAndCodigoDispositivo(idDistribuidor, codigoDispositivo);
        if (actual.isPresent() && estaVigente(actual.get())) {
            return;
        }
        renovarToken();
    }

    @Override
    public Optional<String> obtenerTokenVigente() {
        renovarSiVence();
        return tokenRepository.findByIdDistribuidorAndCodigoDispositivo(idDistribuidor, codigoDispositivo)
                .map(GestoPagoToken::getToken);
    }

    private boolean estaVigente(GestoPagoToken token) {
        LocalDateTime emitido = token.getFechaActualizacion() != null ? token.getFechaActualizacion() : token.getFechaCreacion();
        return token.getToken() != null && emitido != null
                && emitido.plusHours(vigenciaHoras).isAfter(LocalDateTime.now(clock));
    }

    @Override
    public synchronized boolean renovarToken() {
        if (password == null || password.isBlank()) {
            log.warn("No se configuro GESTOPAGO_PASSWORD; se omite la renovacion del token GestoPago");
            return false;
        }
        Instant ahora = clock.instant();
        if (ultimoIntento != null && ahora.isBefore(ultimoIntento.plus(intervaloMinimo))) {
            log.warn("Renovacion de token GestoPago omitida: el ultimo intento fue hace menos de {} min", intervaloMinimo.toMinutes());
            return false;
        }
        if (!circuito.permite()) {
            log.warn("Renovacion de token GestoPago omitida: llamadas en pausa por fallas consecutivas");
            return false;
        }
        ultimoIntento = ahora;
        log.info("Renovando token GestoPago para distribuidor={}", idDistribuidor);
        try {
            GestoPagoAuthResponse response = gestoPagoAuthClient.authenticate(
                    idDistribuidor, codigoDispositivo, password);

            if (response == null || response.getToken() == null) {
                log.error("La respuesta de GestoPago no contiene token");
                circuito.registrarFalla("authenticate");
                return false;
            }

            GestoPagoToken tokenEntity = tokenRepository
                    .findByIdDistribuidorAndCodigoDispositivo(idDistribuidor, codigoDispositivo)
                    .map(existing -> {
                        tokenMapper.updateEntity(response, existing);
                        return existing;
                    })
                    .orElseGet(() -> {
                        GestoPagoToken nuevo = tokenMapper.toEntity(response);
                        nuevo.setIdDistribuidor(idDistribuidor);
                        nuevo.setCodigoDispositivo(codigoDispositivo);
                        nuevo.setActivo(true);
                        return nuevo;
                    });

            tokenRepository.save(tokenEntity);
            circuito.registrarExito();
            log.info("Token GestoPago renovado correctamente");
            return true;

        } catch (FeignException e) {
            // Solo el codigo HTTP: el mensaje de Feign incluye la URL con las credenciales
            log.error("Error al renovar token GestoPago: HTTP {}", e.status());
            circuito.registrarFalla("authenticate");
            return false;
        } catch (RuntimeException e) {
            log.error("Error al renovar token GestoPago: {}", e.getClass().getSimpleName());
            circuito.registrarFalla("authenticate");
            return false;
        }
    }

    @Override
    public Optional<GestoPagoToken> obtenerTokenActivo(Integer idDistribuidor, String codigoDispositivo) {
        return tokenRepository.findByIdDistribuidorAndCodigoDispositivo(idDistribuidor, codigoDispositivo);
    }
}
