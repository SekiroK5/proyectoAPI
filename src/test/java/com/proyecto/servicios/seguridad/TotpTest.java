package com.proyecto.servicios.seguridad;

import org.apache.commons.codec.binary.Base32;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Totp - codigos de un solo uso (RFC 6238)")
class TotpTest {

    /** Secreto de los vectores de prueba del RFC 6238 (SHA1). */
    private static final byte[] CLAVE_RFC = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);
    private static final String SECRETO_RFC = new Base32().encodeToString(CLAVE_RFC).replace("=", "");

    private static Totp totpEn(long segundosEpoch) {
        return new Totp(Clock.fixed(Instant.ofEpochSecond(segundosEpoch), ZoneOffset.UTC));
    }

    @Test
    @DisplayName("Genera los codigos de los vectores oficiales del RFC 6238 (ultimos 6 digitos)")
    void generarCodigo_vectoresRfc() {
        assertThat(Totp.generarCodigo(CLAVE_RFC, 59L / 30)).isEqualTo("287082");
        assertThat(Totp.generarCodigo(CLAVE_RFC, 1111111109L / 30)).isEqualTo("081804");
        assertThat(Totp.generarCodigo(CLAVE_RFC, 1234567890L / 30)).isEqualTo("005924");
    }

    @Test
    @DisplayName("Acepta el codigo del paso actual y devuelve ese paso")
    void verificar_codigoActual() {
        assertThat(totpEn(1234567890L).verificar(SECRETO_RFC, "005924", null)).isEqualTo(1234567890L / 30);
    }

    @Test
    @DisplayName("Tolera un paso de desfase del reloj del telefono")
    void verificar_toleraUnPaso() {
        assertThat(totpEn(1234567890L + 30).verificar(SECRETO_RFC, "005924", null)).isEqualTo(1234567890L / 30);
    }

    @Test
    @DisplayName("Rechaza un codigo de hace mas de un paso")
    void verificar_codigoViejo() {
        assertThat(totpEn(1234567890L + 90).verificar(SECRETO_RFC, "005924", null)).isEqualTo(-1);
    }

    @Test
    @DisplayName("No acepta dos veces el mismo codigo")
    void verificar_noReutiliza() {
        long paso = 1234567890L / 30;
        assertThat(totpEn(1234567890L).verificar(SECRETO_RFC, "005924", paso)).isEqualTo(-1);
    }

    @Test
    @DisplayName("Rechaza codigos con formato invalido")
    void verificar_formatoInvalido() {
        Totp totp = totpEn(1234567890L);
        assertThat(totp.verificar(SECRETO_RFC, "12345", null)).isEqualTo(-1);
        assertThat(totp.verificar(SECRETO_RFC, "abcdef", null)).isEqualTo(-1);
        assertThat(totp.verificar(null, "005924", null)).isEqualTo(-1);
    }

    @Test
    @DisplayName("El secreto generado es Base32 de 32 caracteres y la URI es compatible con las apps")
    void generarSecreto_yUri() {
        Totp totp = totpEn(0);
        String secreto = totp.generarSecreto();

        assertThat(secreto).hasSize(32).matches("[A-Z2-7]+");
        assertThat(totp.generarSecreto()).isNotEqualTo(secreto);
        assertThat(totp.uriOtpauth("productosList", "juan perez", secreto))
                .startsWith("otpauth://totp/productosList:juan%20perez?secret=" + secreto)
                .contains("issuer=productosList", "digits=6", "period=30");
    }
}
