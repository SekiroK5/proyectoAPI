package com.proyecto.servicios.seguridad;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Snippet de cifrado AES de Java de la documentacion de PuntoRed ("Encryption Snipplets"),
 * con la misma estructura y el mismo algoritmo: AES/CBC/PKCS5Padding, llave e IV en Base64 y
 * resultado en Base64. Ajustes respecto al original:
 * <ul>
 *   <li>No imprime la llave ni el IV en consola.</li>
 *   <li>Si falla lanza IllegalStateException en lugar de devolver null, para no enviar {@code signed=null}.</li>
 *   <li>Al descifrar usa UTF-8 explicito en lugar del charset por defecto del sistema.</li>
 * </ul>
 */
// java:S3329, java:S5542 y java:S6432: PuntoRed define el protocolo con AES/CBC/PKCS5Padding y un IV fijo que entrega
// junto con la llave; un IV aleatorio u otro modo haria que GestoPago no pudiera descifrar las peticiones.
@SuppressWarnings({"java:S3329", "java:S5542", "java:S6432"})
public final class AesEncryption {

    private AesEncryption() {
    }

    public enum Mode {
        ENCRYPT, DECRYPT
    }

    public static String aesCipher(Mode mode, String input, String ivBase64, String llaveBase64) {
        byte[] iv = Base64.getDecoder().decode(ivBase64);
        byte[] key = Base64.getDecoder().decode(llaveBase64);
        IvParameterSpec ivspec = new IvParameterSpec(iv);
        SecretKey secretKey = new SecretKeySpec(key, 0, key.length, "AES");
        if (mode == Mode.ENCRYPT) {
            return encrypt(input, secretKey, ivspec);
        }
        return decrypt(input, secretKey, ivspec);
    }

    // Cipher to encrypt
    public static String encrypt(String input, SecretKey secretKey, IvParameterSpec ivspec) {
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivspec);
            return Base64.getEncoder().encodeToString(cipher.doFinal(input.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Error while encrypting: " + e.getClass().getSimpleName(), e);
        }
    }

    // Cypher to decrypt
    public static String decrypt(String input, SecretKey secretKey, IvParameterSpec ivspec) {
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING");
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivspec);
            return new String(cipher.doFinal(Base64.getDecoder().decode(input)), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Error while decrypting: " + e.getClass().getSimpleName(), e);
        }
    }
}
