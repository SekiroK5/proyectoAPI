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

    // ======================== Pagos GestoPago ========================

    public enum EstatusTransaccion {
        /** El saldo ya se aparto y la peticion se esta enviando a GestoPago. */
        PENDIENTE,
        /** GestoPago respondio codigo 01 (o confirmTx 06): el cargo queda firme. */
        APLICADA,
        /** GestoPago rechazo la operacion (o confirmTx 70): el saldo se devolvio. */
        RECHAZADA,
        /** No hubo respuesta (timeout o codigo 82): el saldo sigue apartado hasta confirmarla con confirmTx. */
        PENDIENTE_CONFIRMACION
    }

    // ======================== Inicio de sesion ========================

    /** Segundo factor de autenticacion. Huella y reconocimiento facial (WebAuthn) se agregaran aqui. */
    public enum MetodoA2F {
        TOTP
    }

    /** Lo que debe hacer el cliente despues de validar su contrasena. */
    public enum PasoLogin {
        /** Aun no tiene segundo factor: debe pedir su secreto y confirmar un primer codigo. */
        CONFIGURAR_A2F,
        /** Ya tiene segundo factor: debe enviar el codigo de su app autenticadora. */
        VERIFICAR_A2F
    }
}
