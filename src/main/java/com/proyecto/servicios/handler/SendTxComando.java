package com.proyecto.servicios.handler;

import com.proyecto.servicios.dto.request.TransaccionRequest;

/**
 * Entrada de {@link SendTxHandler}: la peticion del cliente mas el id del cliente que inicio sesion,
 * tomado del token de acceso (nunca del cuerpo, para que nadie pague con una cuenta ajena).
 */
public record SendTxComando(Integer clienteId, TransaccionRequest request) {
}
