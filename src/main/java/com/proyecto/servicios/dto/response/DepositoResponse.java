package com.proyecto.servicios.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class DepositoResponse {

    /** Identificador del deposito en la bitacora. */
    private Integer folio;
    private String numeroCuenta;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "#0.00")
    private BigDecimal monto;
    /** Saldo de la cuenta despues del deposito. */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "#0.00")
    private BigDecimal saldo;
    private LocalDateTime fecha;
}
