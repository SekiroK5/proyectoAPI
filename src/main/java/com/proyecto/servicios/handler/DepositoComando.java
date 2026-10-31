package com.proyecto.servicios.handler;

import com.proyecto.servicios.dto.request.DepositoRequest;

/**
 * Entrada de {@link DepositoHandler}: la peticion mas el id del cliente tomado del token de acceso.
 */
public record DepositoComando(Integer clienteId, DepositoRequest request) {
}
