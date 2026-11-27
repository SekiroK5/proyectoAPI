package com.proyecto.servicios.entity.sf;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Producto del catalogo de GestoPago (getProductList), guardado en la base de datos.
 */
@Entity
@Table(name = "productos_catalogo")
@Getter
@Setter
@NoArgsConstructor
public class ProductoCatalogo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "id_servicio", nullable = false)
    private Integer idServicio;

    @Column(name = "id_producto", nullable = false)
    private Integer idProducto;

    @Column(name = "id_cat_tipo_servicio")
    private Integer idCatTipoServicio;

    @Column(name = "servicio", columnDefinition = "TEXT")
    private String servicio;

    @Column(name = "producto", columnDefinition = "TEXT")
    private String producto;

    @Column(name = "precio", precision = 15, scale = 2)
    private BigDecimal precio;

    @Column(name = "tipo_front")
    private Integer tipoFront;

    @Column(name = "has_digito_verificador")
    private Boolean hasDigitoVerificador;

    @Column(name = "show_ayuda")
    private Boolean showAyuda;

    @Column(name = "tipo_referencia", length = 5)
    private String tipoReferencia;

    @Column(name = "legend", columnDefinition = "TEXT")
    private String legend;

    @Column(name = "fecha_actualizacion", nullable = false)
    private LocalDateTime fechaActualizacion;

    @PrePersist
    @PreUpdate
    void alGuardar() {
        this.fechaActualizacion = LocalDateTime.now();
    }
}
