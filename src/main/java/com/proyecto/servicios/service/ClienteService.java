package com.proyecto.servicios.service;

import com.proyecto.servicios.dto.request.ClienteBusquedaRequest;
import com.proyecto.servicios.dto.request.ClienteConsultaRequest;
import com.proyecto.servicios.dto.request.ClienteCreateRequest;
import com.proyecto.servicios.dto.request.ClienteEliminacionRequest;
import com.proyecto.servicios.dto.request.ClienteUpdateRequest;
import com.proyecto.servicios.dto.response.ApiResponse;
import com.proyecto.servicios.dto.response.ClienteResponse;
import com.proyecto.servicios.dto.response.PaginaResponse;

/**
 * Operaciones de negocio sobre clientes. Las operaciones que localizan a un cliente
 * lo hacen mediante la llave compuesta nombre + correo electronico (nunca por id).
 * Cada metodo devuelve codigo 0 si tuvo exito o codigo 1 con el motivo si no se cumplio una regla de negocio.
 */
public interface ClienteService {

    /** Registra al cliente y le abre automaticamente una cuenta ACTIVA con el saldo inicial del sistema. */
    ApiResponse<ClienteResponse> registrar(ClienteCreateRequest request);

    /** Primera pagina del listado general de clientes. */
    ApiResponse<PaginaResponse<ClienteResponse>> listar();

    /** Busqueda dinamica y paginada; los criterios omitidos se excluyen. */
    ApiResponse<PaginaResponse<ClienteResponse>> buscar(ClienteBusquedaRequest filtro);

    /** Consulta por llave compuesta; codigo 1 (404) si nombre y correo no coinciden con un cliente. */
    ApiResponse<ClienteResponse> consultar(ClienteConsultaRequest request);

    /** Actualizacion parcial: solo modifica los campos enviados. */
    ApiResponse<ClienteResponse> actualizar(ClienteUpdateRequest request);

    /** Eliminacion logica: el cliente queda inactivo y su cuenta INACTIVA; no se borra ningun registro. */
    ApiResponse<ClienteResponse> eliminar(ClienteEliminacionRequest request);
}
