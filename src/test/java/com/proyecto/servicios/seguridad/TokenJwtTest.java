package com.proyecto.servicios.seguridad;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("TokenJwt - tokens temporales y de acceso")
class TokenJwtTest {

    static final String SECRETO = "secreto-de-pruebas-de-al-menos-32-bytes!";

    private final TokenJwt tokenJwt = new TokenJwt(SECRETO, "productosList", 30, 5, Clock.systemUTC());

    @Test
    @DisplayName("El token de acceso lo acepta el decoder del resource server")
    void acceso_valido() {
        Jwt jwt = tokenJwt.decoderAcceso().decode(tokenJwt.emitirAcceso(7, "juanperez95"));

        assertThat(jwt.getSubject()).isEqualTo("7");
        assertThat(jwt.getClaimAsString(TokenJwt.CLAIM_USUARIO)).isEqualTo("juanperez95");
        assertThat(jwt.getClaimAsString(TokenJwt.CLAIM_TIPO)).isEqualTo(TokenJwt.TIPO_ACCESO);
    }

    @Test
    @DisplayName("El token temporal A2F no sirve como token de acceso")
    void temporal_noEsAcceso() {
        String temporal = tokenJwt.emitirA2F(7, "juanperez95");

        assertThatThrownBy(() -> tokenJwt.decoderAcceso().decode(temporal)).isInstanceOf(JwtException.class);
        assertThat(tokenJwt.validarA2F(temporal)).contains(7);
    }

    @Test
    @DisplayName("El token de acceso no sirve como token temporal A2F")
    void acceso_noEsTemporal() {
        assertThat(tokenJwt.validarA2F(tokenJwt.emitirAcceso(7, "juanperez95"))).isEmpty();
    }

    @Test
    @DisplayName("Rechaza tokens firmados con otro secreto o mal formados")
    void firmaAjena() {
        TokenJwt otro = new TokenJwt("otro-secreto-distinto-de-al-menos-32-bytes", "productosList", 30, 5, Clock.systemUTC());

        assertThat(tokenJwt.validarA2F(otro.emitirA2F(7, "juanperez95"))).isEmpty();
        assertThat(tokenJwt.validarA2F("no-es-un-jwt")).isEmpty();
        assertThatThrownBy(() -> tokenJwt.decoderAcceso().decode(otro.emitirAcceso(7, "x"))).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("Exige un secreto de al menos 32 bytes")
    void secretoCorto() {
        assertThatThrownBy(() -> new TokenJwt("corto", "productosList", 30, 5, Clock.systemUTC()))
                .isInstanceOf(IllegalStateException.class);
    }
}
