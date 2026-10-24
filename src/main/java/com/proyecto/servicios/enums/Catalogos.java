package com.proyecto.servicios.enums;

/**
 * Catalogos de valores permitidos del onboarding, agrupados por lo que describen.
 * Se guardan en base de datos como texto (@Enumerated(EnumType.STRING)) y cada columna
 * tiene una restriccion CHECK con los mismos valores.
 */
public final class Catalogos {

    private Catalogos() {
    }

    // ======================== Datos personales del cliente ========================

    /** M = Masculino, F = Femenino, O = Otro. */
    public enum Sexo {
        M, F, O
    }

    public enum EstadoCivil {
        SOLTERO, CASADO, DIVORCIADO, VIUDO, UNION_LIBRE
    }

    // ======================== Cuenta bancaria ========================

    /** Solo los clientes activos pueden tener cuentas ACTIVAS. */
    public enum EstatusCuenta {
        ACTIVA, INACTIVA
    }
}
