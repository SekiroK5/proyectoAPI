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
        PENDIENTE_CONFIRMACION,
        /** confirmTx no la resolvio tras el maximo de intentos: el saldo sigue apartado y la revisa una persona. */
        REVISION_MANUAL
    }

    /** Resultado de verificar una referencia antes de pagar un servicio. */
    public enum ResultadoVerificacion {
        /** GestoPago (verifyReference) confirmo que la referencia es valida. */
        VALIDA,
        /** GestoPago o la validacion de formato la rechazaron. */
        INVALIDA,
        /** El servicio no ofrece verifyReference en GestoPago: solo se valido el formato. */
        FORMATO_VALIDO
    }

    // ======================== Inicio de sesion ========================

    /** Segundo factor de autenticacion. Huella y reconocimiento facial (WebAuthn) se agregaran aqui. */
    public enum MetodoA2F {
        TOTP
    }

    /** Para que sirve un codigo enviado por correo: cada uno solo vale para su proposito. */
    public enum PropositoCodigo {
        /** Confirmar que el correo es del cliente (al registrarse). */
        CONFIRMAR_CORREO,
        /** Segundo factor del inicio de sesion, en lugar de la app autenticadora. */
        INICIO_SESION,
        /** Autorizar un pago o un deposito, en lugar de la app autenticadora. */
        OPERACION
    }

    /** Lo que debe hacer el cliente despues de validar su contrasena. */
    public enum PasoLogin {
        /** Su correo aun no esta confirmado: debe enviar el codigo que se le mando por correo. */
        VERIFICAR_CORREO,
        /** Aun no tiene segundo factor: debe pedir su secreto y confirmar un primer codigo. */
        CONFIGURAR_A2F,
        /** Ya tiene segundo factor: debe enviar el codigo de su app autenticadora. */
        VERIFICAR_A2F,
        /** Eligio recibir el codigo del inicio de sesion por correo: debe enviar ese codigo. */
        VERIFICAR_CODIGO_CORREO
    }
}
