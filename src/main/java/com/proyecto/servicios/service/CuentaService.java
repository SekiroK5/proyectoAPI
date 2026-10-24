package com.proyecto.servicios.service;

import com.proyecto.servicios.dto.request.CuentaBusquedaRequest;
import com.proyecto.servicios.dto.request.CuentaConsultaRequest;
import com.proyecto.servicios.dto.response.ApiResponse;
import com.proyecto.servicios.dto.response.CuentaResponse;
import com.proyecto.servicios.dto.response.PaginaResponse;

public interface CuentaService {

    /** Consulta una cuenta y su saldo disponible; codigo 1 (404) si no existe. */
    ApiResponse<CuentaResponse> consultar(CuentaConsultaRequest request);

    /** Busqueda dinamica y paginada (p. ej. estatus = ACTIVA para las cuentas activas). */
    ApiResponse<PaginaResponse<CuentaResponse>> buscar(CuentaBusquedaRequest filtro);
}
