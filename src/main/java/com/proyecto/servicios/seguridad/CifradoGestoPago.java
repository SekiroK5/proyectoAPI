package com.proyecto.servicios.seguridad;

import com.proyecto.servicios.seguridad.AesEncryption.Mode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Base64;

/**
 * Cifra el payload de GestoPago con {@link AesEncryption} usando la llave (SECRET_KEY) y el IV que
 * entrega PuntoRed. Ninguno de los dos vive en el codigo ni en application.properties: se toman de las
 * variables de entorno GESTOPAGO_SECRET_KEY y GESTOPAGO_IV, o del archivo externo config/gestopago.properties.
 * <p>
 * Si no estan configurados la aplicacion arranca igual, pero no se envian pagos (no se hace ningun cargo).
 */
@Component
@Slf4j
public class CifradoGestoPago {

    private static final int BYTES_LLAVE = 32;
    private static final int BYTES_IV = 16;

    private final String llave;
    private final String iv;

    public CifradoGestoPago(@Value("${gestopago.cifrado.llave:}") String llave,
                            @Value("${gestopago.cifrado.iv:}") String iv) {
        this.llave = llave == null || llave.isBlank() ? null : llave.trim();
        this.iv = iv == null || iv.isBlank() ? null : iv.trim();
        if (!configurado()) {
            log.warn("No se configuraron GESTOPAGO_SECRET_KEY y GESTOPAGO_IV: no se podran enviar pagos a GestoPago");
            return;
        }
        // Una llave o IV mal capturados se detectan al arrancar, no en el primer pago
        if (Base64.getDecoder().decode(this.llave).length != BYTES_LLAVE || Base64.getDecoder().decode(this.iv).length != BYTES_IV) {
            throw new IllegalStateException("GESTOPAGO_SECRET_KEY debe ser de 32 bytes y GESTOPAGO_IV de 16 bytes (Base64)");
        }
    }

    public boolean configurado() {
        return llave != null && iv != null;
    }

    /** Cifra el JSON y lo devuelve en Base64 (valor del parametro {@code signed}). */
    public String cifrar(String json) {
        return AesEncryption.AESCipher(Mode.ENCRYPT, json, ivConfigurado(), llaveConfigurada());
    }

    public String descifrar(String cifrado) {
        return AesEncryption.AESCipher(Mode.DECRYPT, cifrado, ivConfigurado(), llaveConfigurada());
    }

    private String llaveConfigurada() {
        if (!configurado()) {
            throw new CifradoNoConfiguradoException();
        }
        return llave;
    }

    private String ivConfigurado() {
        if (!configurado()) {
            throw new CifradoNoConfiguradoException();
        }
        return iv;
    }

    /** Falta la llave o el IV de GestoPago: la peticion no se puede armar ni enviar. */
    public static class CifradoNoConfiguradoException extends IllegalStateException {
        public CifradoNoConfiguradoException() {
            super("No se configuraron la llave y el IV de cifrado de GestoPago");
        }
    }
}
