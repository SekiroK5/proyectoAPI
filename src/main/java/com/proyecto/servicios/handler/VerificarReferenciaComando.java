package com.proyecto.servicios.handler;

import com.proyecto.servicios.dto.request.VerificacionReferenciaRequest;

/**
 * Entrada de {@link VerificarReferenciaHandler}: la peticion mas el id del cliente tomado del token de acceso.
 */
public record VerificarReferenciaComando(Integer clienteId, VerificacionReferenciaRequest request) {
}
