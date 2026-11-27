package com.proyecto.servicios.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.proyecto.servicios.enums.Catalogos.ResultadoVerificacion;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class VerificacionReferenciaResponse {

    /** Se envia como folioVerificacion al pagar. Vence en {@code expira} y sirve para un solo pago. */
    private String folio;
    private ResultadoVerificacion resultado;
    /** true si la verifico GestoPago (verifyReference); false si el servicio solo permite validar el formato. */
    private Boolean verificadaConGestoPago;
    private String mensaje;
    /** Adeudo informado por GestoPago: positivo = pendiente, 0 = al corriente, negativo = saldo a favor. */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "#0.00")
    private BigDecimal saldoPendiente;
    private Integer idServicio;
    private Integer idProducto;
    private String servicio;
    private String producto;
    private String referencia;
    private String logoUrl;
    private LocalDateTime expira;
}
