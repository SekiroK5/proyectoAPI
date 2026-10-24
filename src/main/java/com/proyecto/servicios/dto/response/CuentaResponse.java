package com.proyecto.servicios.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.proyecto.servicios.enums.Catalogos.EstatusCuenta;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class CuentaResponse {

    private String numeroCuenta;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "#0.00")
    private BigDecimal saldo;
    private EstatusCuenta estatus;
    private LocalDateTime fechaApertura;
    private String nombreCliente;
    private String correoCliente;
    private Boolean clienteActivo;
}
