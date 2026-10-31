package com.proyecto.servicios.entity.sf;

import com.proyecto.servicios.enums.Catalogos.EstadoCivil;
import com.proyecto.servicios.enums.Catalogos.MetodoA2F;
import com.proyecto.servicios.enums.Catalogos.Sexo;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "clientes")
@Getter
@Setter
@NoArgsConstructor
public class Cliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "nombre", nullable = false, length = 50)
    private String nombre;

    @Column(name = "segundo_nombre", length = 50)
    private String segundoNombre;

    @Column(name = "apellido_paterno", nullable = false, length = 50)
    private String apellidoPaterno;

    @Column(name = "apellido_materno", nullable = false, length = 50)
    private String apellidoMaterno;

    @Column(name = "fecha_nacimiento", nullable = false)
    private LocalDate fechaNacimiento;

    @Column(name = "curp", nullable = false, unique = true, length = 18, updatable = false)
    private String curp;

    @Column(name = "rfc", nullable = false, unique = true, length = 13, updatable = false)
    private String rfc;

    @Enumerated(EnumType.STRING)
    @Column(name = "sexo", nullable = false, length = 1)
    private Sexo sexo;

    @Column(name = "nacionalidad", nullable = false, length = 50)
    private String nacionalidad;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_civil", nullable = false, length = 15)
    private EstadoCivil estadoCivil;

    @Column(name = "correo_electronico", nullable = false, unique = true, length = 100)
    private String correoElectronico;

    @Column(name = "telefono_movil", nullable = false, length = 10)
    private String telefonoMovil;

    @Column(name = "telefono_alternativo", length = 10)
    private String telefonoAlternativo;

    @Column(name = "ocupacion", nullable = false, length = 100)
    private String ocupacion;

    @Column(name = "empresa", nullable = false, length = 100)
    private String empresa;

    @Column(name = "ingreso_mensual", nullable = false, precision = 12, scale = 2)
    private BigDecimal ingresoMensual;

    @Column(name = "nombre_usuario", nullable = false, unique = true, length = 50, updatable = false)
    private String nombreUsuario;

    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "domicilio_id", referencedColumnName = "id", nullable = false, unique = true)
    private Domicilio domicilio;

    @OneToOne(mappedBy = "cliente", fetch = FetchType.LAZY)
    private Cuenta cuenta;

    @Column(name = "activo", nullable = false)
    private Boolean activo = true;

    // ---------------- Inicio de sesion ----------------

    @Column(name = "contrasena_hash", length = 60)
    private String contrasenaHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "a2f_metodo", length = 20)
    private MetodoA2F a2fMetodo;

    @Column(name = "a2f_secreto", length = 32)
    private String a2fSecreto;

    @Column(name = "a2f_activo", nullable = false)
    private Boolean a2fActivo = false;

    @Column(name = "a2f_ultimo_paso")
    private Long a2fUltimoPaso;

    @Column(name = "intentos_fallidos", nullable = false)
    private Integer intentosFallidos = 0;

    @Column(name = "bloqueado_hasta")
    private LocalDateTime bloqueadoHasta;

    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    @Column(name = "fecha_actualizacion", nullable = false)
    private LocalDateTime fechaActualizacion;

    @PrePersist
    void onCreate() {
        this.fechaCreacion = LocalDateTime.now();
        this.fechaActualizacion = LocalDateTime.now();
        if (this.activo == null) {
            this.activo = true;
        }
    }

    @PreUpdate
    void onUpdate() {
        this.fechaActualizacion = LocalDateTime.now();
    }
}
