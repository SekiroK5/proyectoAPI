package com.proyecto.servicios.dto.request;

import com.proyecto.servicios.enums.Catalogos.EstatusCuenta;
import com.proyecto.servicios.util.Texto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import static com.proyecto.servicios.validation.ValidacionPatrones.CURP;

/**
 * Criterios combinables para buscar cuentas (p. ej. estatus = ACTIVA para las cuentas activas).
 */
@Getter
@Setter
@NoArgsConstructor
public class CuentaBusquedaRequest extends PaginacionRequest {

    @Schema(example = "ACTIVA")
    private EstatusCuenta estatus;

    @Schema(description = "CURP del titular", example = "PELJ950520HDFRRN01")
    @Pattern(regexp = CURP, message = "La CURP no cumple con el formato oficial.")
    private String curp;

    public void setCurp(String curp) {
        this.curp = Texto.mayusculas(curp);
    }
}
