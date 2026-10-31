package com.proyecto.servicios.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.proyecto.servicios.enums.Catalogos.EstatusTransaccion;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class TransaccionResponse {

    private Integer id;
    private String upc;
    private EstatusTransaccion estatus;
    private String numeroCuenta;
    private Integer idServicio;
    private Integer idProducto;
    private String telefono;
    private String referencia;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "#0.00")
    private BigDecimal monto;
    /** Saldo de la cuenta despues de la operacion. */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "#0.00")
    private BigDecimal saldoCuenta;
    private String numAutorizacion;
    private String idTx;
    /** PIN, folio o URL de descarga en tarjetas de regalo; se debe entregar al cliente. */
    private String pin;
    private String codigoGestoPago;
    private String mensajeGestoPago;
    private LocalDateTime fecha;
}
