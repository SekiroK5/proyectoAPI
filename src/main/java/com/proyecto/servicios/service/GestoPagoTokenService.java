package com.proyecto.servicios.service;

import com.proyecto.servicios.entity.gestopago.GestoPagoToken;

import java.util.Optional;

/**
 * Token de GestoPago/PuntoRed. Segun su documentacion dura 24 h, se debe pedir uno al dia y no esta
 * permitido pedir uno nuevo por cada peticion: se reutiliza el guardado mientras siga vigente.
 */
public interface GestoPagoTokenService {

    /**
     * Pide un token nuevo (por ejemplo, cuando GestoPago responde 401/403). Como maximo un intento cada
     * {@code gestopago.auth.intervalo-minimo-minutos}, y nunca mientras las llamadas esten en pausa.
     *
     * @return true si se obtuvo y guardo un token nuevo
     */
    boolean renovarToken();

    /** Renueva el token solo si no existe o ya cumplio su vigencia. Lo ejecuta el scheduler. */
    void renovarSiVence();

    /** Token vigente listo para usarse; lo renueva antes si ya vencio. Vacio si no fue posible obtenerlo. */
    Optional<String> obtenerTokenVigente();

    Optional<GestoPagoToken> obtenerTokenActivo(Integer idDistribuidor, String codigoDispositivo);
}
