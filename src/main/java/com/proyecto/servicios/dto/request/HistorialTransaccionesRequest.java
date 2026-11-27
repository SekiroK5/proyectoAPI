package com.proyecto.servicios.dto.request;

import com.proyecto.servicios.enums.Catalogos.EstatusTransaccion;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Historial de pagos del usuario de la sesion, paginado y del mas reciente al mas antiguo.
 */
@Getter
@Setter
@NoArgsConstructor
public class HistorialTransaccionesRequest extends PaginacionRequest {

    @Schema(description = "Opcional: solo transacciones con este estatus", example = "APLICADA")
    private EstatusTransaccion estatus;
}
