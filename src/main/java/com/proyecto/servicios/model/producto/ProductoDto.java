package com.proyecto.servicios.model.producto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
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
}
