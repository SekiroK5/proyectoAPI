package com.proyecto.servicios.service;

import com.proyecto.servicios.entity.gestopago.GestoPagoToken;

import java.util.Optional;

/**
 * Token de GestoPago/PuntoRed. Segun su documentacion dura 24 h, se debe pedir uno al dia y no esta
 * permitido pedir uno nuevo por cada peticion: se reutiliza el guardado mientras siga vigente.
 */
public interface GestoPagoTokenService {

    /** Fuerza la obtencion de un token nuevo (por ejemplo, cuando GestoPago responde 401/403). */
    void renovarToken();

    /** Renueva el token solo si no existe o ya cumplio su vigencia. Lo ejecuta el scheduler. */
    void renovarSiVence();

    /** Token vigente listo para usarse; lo renueva antes si ya vencio. Vacio si no fue posible obtenerlo. */
    Optional<String> obtenerTokenVigente();

    Optional<GestoPagoToken> obtenerTokenActivo(Integer idDistribuidor, String codigoDispositivo);
}
