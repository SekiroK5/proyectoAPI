package com.proyecto.servicios.entity.sf;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Token de acceso cerrado con /auth/logout; se guarda solo hasta su expiracion. */
@Entity
@Table(name = "tokens_revocados")
@Getter
@Setter
@NoArgsConstructor
public class TokenRevocado {

    @Id
    @Column(name = "jti", length = 36)
    private String jti;

    @Column(name = "expira", nullable = false)
    private LocalDateTime expira;
}
