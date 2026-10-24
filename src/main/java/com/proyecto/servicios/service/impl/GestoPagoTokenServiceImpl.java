package com.proyecto.servicios.service.impl;

import com.proyecto.servicios.client.GestoPagoAuthClient;
import com.proyecto.servicios.entity.gestopago.GestoPagoToken;
import com.proyecto.servicios.mapper.GestoPagoTokenMapper;
import com.proyecto.servicios.model.gestopago.GestoPagoAuthResponse;
import com.proyecto.servicios.repository.gestopago.GestoPagoTokenRepository;
import com.proyecto.servicios.service.GestoPagoTokenService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
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

    public GestoPagoTokenServiceImpl(GestoPagoAuthClient gestoPagoAuthClient,
                                     GestoPagoTokenRepository tokenRepository,
                                     GestoPagoTokenMapper tokenMapper,
                                     Clock clock) {
        this.gestoPagoAuthClient = gestoPagoAuthClient;
        this.tokenRepository = tokenRepository;
        this.tokenMapper = tokenMapper;
        this.clock = clock;
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
    public void renovarToken() {
        if (password == null || password.isBlank()) {
            log.warn("No se configuro GESTOPAGO_PASSWORD; se omite la renovacion del token GestoPago");
            return;
        }
        log.info("Renovando token GestoPago para distribuidor={}", idDistribuidor);
        try {
            GestoPagoAuthResponse response = gestoPagoAuthClient.authenticate(
                    idDistribuidor, codigoDispositivo, password);

            if (response == null || response.getToken() == null) {
                log.error("La respuesta de GestoPago no contiene token");
                return;
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
            log.info("Token GestoPago renovado correctamente");

        } catch (Exception e) {
            log.error("Error al renovar token GestoPago: {}", e.getMessage(), e);
        }
    }

    @Override
    public Optional<GestoPagoToken> obtenerTokenActivo(Integer idDistribuidor, String codigoDispositivo) {
        return tokenRepository.findByIdDistribuidorAndCodigoDispositivo(idDistribuidor, codigoDispositivo);
    }
}
