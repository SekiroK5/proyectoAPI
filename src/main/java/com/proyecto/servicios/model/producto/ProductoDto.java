package com.proyecto.servicios.model.producto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProductoDto {

    private Integer idServicio;
    private Integer idProducto;
    private Integer idCatTipoServicio;
    private String servicio;
    private String producto;
    private Double precio;
    private Integer tipoFront;
    private Boolean hasDigitoVerificador;
    private Boolean showAyuda;
    private String tipoReferencia;
    private String legend;
    /** Logotipo del proveedor (URL publica de GestoPago). */
    private String logoUrl;
    /** Imagen de ayuda para encontrar la referencia en el recibo; solo en pago de servicios. */
    private String imagenReferenciaUrl;
    /** Nombre de la categoria segun idCatTipoServicio. */
    private String categoria;
    /** RECARGA, PAGO_SERVICIO o NO_SOPORTADA. */
    private String tipoOperacion;
    /** El pago exige verificar la referencia antes (POST /api/v1/referencias/verificacion). */
    private Boolean requiereVerificacion;
    /** GestoPago ofrece verifyReference para este servicio; si no, la verificacion es solo de formato. */
    private Boolean verificableEnGestoPago;
}
