package com.proyecto.servicios.service;

import com.proyecto.servicios.dto.request.DetalleTransaccionRequest;
import com.proyecto.servicios.dto.request.HistorialTransaccionesRequest;
import com.proyecto.servicios.dto.response.ApiResponse;
import com.proyecto.servicios.dto.response.PaginaResponse;
import com.proyecto.servicios.dto.response.TransaccionResponse;

/**
 * Consulta de los pagos del usuario de la sesion. Nunca devuelve transacciones de otros clientes.
 */
public interface TransaccionService {

    ApiResponse<PaginaResponse<TransaccionResponse>> historial(Integer clienteId, HistorialTransaccionesRequest request);

    /** codigo 1 (404) si la transaccion no existe o no es del cliente. */
    ApiResponse<TransaccionResponse> detalle(Integer clienteId, DetalleTransaccionRequest request);
}
