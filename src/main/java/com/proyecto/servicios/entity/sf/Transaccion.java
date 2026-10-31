package com.proyecto.servicios.entity.sf;

import com.proyecto.servicios.enums.Catalogos.EstatusTransaccion;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "transacciones")
@Getter
@Setter
@NoArgsConstructor
public class Transaccion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cuenta_id", nullable = false, updatable = false)
    private Cuenta cuenta;

    @Column(name = "upc", nullable = false, unique = true, length = 15, updatable = false)
    private String upc;

    @Column(name = "id_servicio", nullable = false, updatable = false)
    private Integer idServicio;

    @Column(name = "id_producto", nullable = false, updatable = false)
    private Integer idProducto;

    @Column(name = "telefono", nullable = false, length = 20, updatable = false)
    private String telefono;

    @Column(name = "referencia", length = 100, updatable = false)
    private String referencia;

    @Column(name = "monto", nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal monto;

    @Column(name = "hora_local", nullable = false, length = 14, updatable = false)
    private String horaLocal;

    @Column(name = "unidad", nullable = false, length = 25, updatable = false)
    private String unidad;

    @Enumerated(EnumType.STRING)
    @Column(name = "estatus", nullable = false, length = 25)
    private EstatusTransaccion estatus;

    @Column(name = "payload_enviado", nullable = false, columnDefinition = "TEXT", updatable = false)
    private String payloadEnviado;

    @Column(name = "respuesta", columnDefinition = "TEXT")
    private String respuesta;

    @Column(name = "codigo_respuesta", length = 4)
    private String codigoRespuesta;

    @Column(name = "texto_respuesta", length = 500)
    private String textoRespuesta;

    @Column(name = "id_tx", length = 30)
    private String idTx;

    @Column(name = "num_autorizacion", length = 60)
    private String numAutorizacion;

    @Column(name = "pin", length = 250)
    private String pin;

    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    @Column(name = "fecha_actualizacion", nullable = false)
    private LocalDateTime fechaActualizacion;

    @PrePersist
    void onCreate() {
        this.fechaCreacion = LocalDateTime.now();
        this.fechaActualizacion = LocalDateTime.now();
    }

    @PreUpdate
    void onUpdate() {
        this.fechaActualizacion = LocalDateTime.now();
    }
}
