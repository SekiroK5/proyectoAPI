package com.proyecto.servicios.entity.sf;

import com.proyecto.servicios.enums.Catalogos.ResultadoVerificacion;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "verificaciones_referencia")
@Getter
@Setter
@NoArgsConstructor
public class VerificacionReferencia {

    /** Folio que el cliente envia al pagar para demostrar que verifico la referencia. */
    @Id
    @Column(name = "folio", length = 36)
    private String folio;

    @Column(name = "cliente_id", nullable = false, updatable = false)
    private Integer clienteId;

    @Column(name = "id_servicio", nullable = false, updatable = false)
    private Integer idServicio;

    @Column(name = "id_producto", nullable = false, updatable = false)
    private Integer idProducto;

    @Column(name = "referencia", nullable = false, length = 100, updatable = false)
    private String referencia;

    @Enumerated(EnumType.STRING)
    @Column(name = "resultado", nullable = false, length = 20, updatable = false)
    private ResultadoVerificacion resultado;

    @Column(name = "verificada_gestopago", nullable = false, updatable = false)
    private Boolean verificadaGestoPago;

    @Column(name = "mensaje", length = 500, updatable = false)
    private String mensaje;

    @Column(name = "saldo_pendiente", precision = 15, scale = 2, updatable = false)
    private BigDecimal saldoPendiente;

    @Column(name = "usada", nullable = false)
    private Boolean usada = false;

    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    @Column(name = "expira", nullable = false, updatable = false)
    private LocalDateTime expira;
}
