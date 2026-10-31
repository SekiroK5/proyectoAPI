package com.proyecto.servicios.seguridad;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("CifradoGestoPago - AES-256-CBC de la documentacion de PuntoRed")
class CifradoGestoPagoTest {

    /** Par de prueba publicado en la documentacion ("Test key pair"). */
    static final String LLAVE = "HPo7OLqB4Fkk4E2yGOtwqw8H5fHR9kNx67OR4g4UdlA=";
    static final String IV = "p5ldmBPdd/9pjC0bDC/nSg==";

    /** "Encryption Expected Result" de la documentacion y el payload que le corresponde. */
    private static final String ESPERADO_DOC = "+LWZ7icI60/DAI8ggv/SpDvJpFYeaYOZ5J7hPGK7g/87r9PRJfgFRBoBLYpBxW33ouvuwQPnhhKHXqzbiqLhMSH4gSH8SMvW6PvHfeklNfTrZnbgWFWDPSy/nqFCP0B/gKavafHG0tW5UP4KPwyhECZK2pLT+I3qYAA85uQaAPQ=";
    private static final String PAYLOAD_DOC = "{'idProducto':582,'idServicio':133,'telefono':'5555555551','horaLocal':'20220101235900','upc':'test_UPC_83_182'}";

    private final CifradoGestoPago cifrado = new CifradoGestoPago(LLAVE, IV);

    @Test
    @DisplayName("Produce exactamente el resultado de ejemplo de la documentación")
    void cifrar_vectorDocumentacion() {
        assertThat(cifrado.cifrar(PAYLOAD_DOC)).isEqualTo(ESPERADO_DOC);
    }

    @Test
    @DisplayName("Descifra el resultado de ejemplo de la documentación")
    void descifrar_vectorDocumentacion() {
        assertThat(cifrado.descifrar(ESPERADO_DOC)).isEqualTo(PAYLOAD_DOC);
    }

    @Test
    @DisplayName("Ida y vuelta con acentos (UTF-8) y el mismo texto siempre cifra igual (IV fijo)")
    void idaYVuelta() {
        String json = "{\"referencia\":\"ÑANDÚ-123\",\"montoPago\":601.99}";

        assertThat(cifrado.descifrar(cifrado.cifrar(json))).isEqualTo(json);
        assertThat(cifrado.cifrar(json)).isEqualTo(cifrado.cifrar(json));
    }

    @Test
    @DisplayName("El snippet AesEncryption.AESCipher produce el mismo vector de la documentación")
    void snippet_vectorDocumentacion() {
        assertThat(AesEncryption.AESCipher(AesEncryption.Mode.ENCRYPT, PAYLOAD_DOC, IV, LLAVE)).isEqualTo(ESPERADO_DOC);
        assertThat(AesEncryption.AESCipher(AesEncryption.Mode.DECRYPT, ESPERADO_DOC, IV, LLAVE)).isEqualTo(PAYLOAD_DOC);
    }

    @Test
    @DisplayName("Sin llave ni IV configurados arranca, pero no cifra")
    void sinConfigurar() {
        CifradoGestoPago sinLlaves = new CifradoGestoPago("", " ");

        assertThat(sinLlaves.configurado()).isFalse();
        assertThatThrownBy(() -> sinLlaves.cifrar(PAYLOAD_DOC)).isInstanceOf(CifradoGestoPago.CifradoNoConfiguradoException.class);
    }

    @Test
    @DisplayName("Rechaza llaves o IV con longitud distinta a 32 y 16 bytes")
    void longitudesInvalidas() {
        assertThatThrownBy(() -> new CifradoGestoPago(IV, IV)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new CifradoGestoPago(LLAVE, LLAVE)).isInstanceOf(IllegalStateException.class);
    }
}
