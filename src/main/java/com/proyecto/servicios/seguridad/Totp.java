package com.proyecto.servicios.seguridad;

import org.apache.commons.codec.binary.Base32;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Clock;

/**
 * Codigos de un solo uso basados en tiempo (TOTP, RFC 6238) compatibles con Google Authenticator,
 * Microsoft Authenticator y similares: HMAC-SHA1, 6 digitos y pasos de 30 segundos.
 */
@Component
public class Totp {

    private static final int DIGITOS = 6;
    private static final int PERIODO_SEGUNDOS = 30;
    /** Pasos de tolerancia antes y despues del actual, por desfase de reloj del telefono. */
    private static final int VENTANA = 1;
    private static final int BYTES_SECRETO = 20;

    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final Base32 base32 = new Base32();

    public Totp(Clock clock) {
        this.clock = clock;
    }

    /** Genera un secreto aleatorio de 160 bits en Base32 (32 caracteres, sin relleno). */
    public String generarSecreto() {
        byte[] bytes = new byte[BYTES_SECRETO];
        random.nextBytes(bytes);
        return base32.encodeToString(bytes).replace("=", "");
    }

    /** URI que la app autenticadora lee desde un codigo QR para dar de alta la cuenta. */
    public String uriOtpauth(String emisor, String cuenta, String secreto) {
        String etiqueta = codificar(emisor) + ":" + codificar(cuenta);
        return "otpauth://totp/" + etiqueta + "?secret=" + secreto + "&issuer=" + codificar(emisor)
                + "&algorithm=SHA1&digits=" + DIGITOS + "&period=" + PERIODO_SEGUNDOS;
    }

    /**
     * Verifica el codigo contra el paso actual y los vecinos de la ventana.
     *
     * @param ultimoPasoUsado ultimo paso aceptado para este secreto (null si nunca se uso); los pasos
     *                        iguales o anteriores se rechazan para que un codigo no sirva dos veces
     * @return el paso aceptado, o -1 si el codigo no es valido
     */
    public long verificar(String secreto, String codigo, Long ultimoPasoUsado) {
        if (secreto == null || codigo == null || !codigo.matches("\\d{" + DIGITOS + "}")) {
            return -1;
        }
        byte[] clave = base32.decode(secreto);
        long pasoActual = pasoActual();
        for (long paso = pasoActual - VENTANA; paso <= pasoActual + VENTANA; paso++) {
            if (ultimoPasoUsado != null && paso <= ultimoPasoUsado) {
                continue;
            }
            if (igualEnTiempoConstante(generarCodigo(clave, paso), codigo)) {
                return paso;
            }
        }
        return -1;
    }

    long pasoActual() {
        return clock.millis() / 1000 / PERIODO_SEGUNDOS;
    }

    /** Algoritmo HOTP (RFC 4226) sobre el contador de tiempo. */
    public static String generarCodigo(byte[] clave, long paso) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(clave, "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(paso).array());
            int offset = hash[hash.length - 1] & 0x0F;
            int binario = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);
            int codigo = binario % (int) Math.pow(10, DIGITOS);
            return String.format("%0" + DIGITOS + "d", codigo);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA1 no disponible", e);
        }
    }

    private static boolean igualEnTiempoConstante(String a, String b) {
        return java.security.MessageDigest.isEqual(a.getBytes(StandardCharsets.US_ASCII), b.getBytes(StandardCharsets.US_ASCII));
    }

    private static String codificar(String texto) {
        return URLEncoder.encode(texto, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
