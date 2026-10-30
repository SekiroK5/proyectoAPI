package com.proyecto.servicios.entity.sf;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "depositos")
@Getter
@Setter
@NoArgsConstructor
public class Deposito {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cuenta_id", nullable = false, updatable = false)
    private Cuenta cuenta;

    @Column(name = "monto", nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal monto;

    @Column(name = "saldo_resultante", nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal saldoResultante;

    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    @PrePersist
    void onCreate() {
        this.fechaCreacion = LocalDateTime.now();
    }
}
