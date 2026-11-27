package com.proyecto.servicios.service;

import com.proyecto.servicios.dto.request.CuentaBusquedaRequest;
import com.proyecto.servicios.dto.request.CuentaConsultaRequest;
import com.proyecto.servicios.dto.response.ApiResponse;
import com.proyecto.servicios.dto.response.CuentaResponse;
import com.proyecto.servicios.dto.response.PaginaResponse;

public interface CuentaService {

    /** Cuenta del cliente de la sesion; una cuenta ajena se responde como inexistente (404). */
    ApiResponse<CuentaResponse> consultar(Integer clienteId, CuentaConsultaRequest request);

    /** Busqueda dinamica y paginada (p. ej. estatus = ACTIVA para las cuentas activas). */
    ApiResponse<PaginaResponse<CuentaResponse>> buscar(CuentaBusquedaRequest filtro);
}
