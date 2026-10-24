package com.proyecto.servicios.seguridad;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Emite y valida los JWT firmados con HS256. Hay dos tipos que nunca son intercambiables:
 * <ul>
 *   <li>{@code A2F}: token temporal emitido tras validar la contrasena; solo sirve para enviar el codigo A2F.</li>
 *   <li>{@code ACCESO}: token final que exigen los endpoints protegidos.</li>
 * </ul>
 */
@Component
public class TokenJwt {

    public static final String CLAIM_TIPO = "tipo";
    public static final String CLAIM_USUARIO = "usuario";
    public static final String TIPO_A2F = "A2F";
    public static final String TIPO_ACCESO = "ACCESO";

    private static final int LONGITUD_MINIMA_SECRETO = 32;

    private final JwtEncoder encoder;
    private final JwtDecoder decoderAcceso;
    private final JwtDecoder decoderA2F;
    private final String emisor;
    private final Duration vigenciaAcceso;
    private final Duration vigenciaA2F;
    private final Clock clock;

    public TokenJwt(@Value("${seguridad.jwt.secreto}") String secreto,
                    @Value("${seguridad.jwt.emisor}") String emisor,
                    @Value("${seguridad.jwt.expiracion-minutos}") long minutosAcceso,
                    @Value("${seguridad.a2f.expiracion-minutos}") long minutosA2F,
                    Clock clock) {
        if (secreto == null || secreto.getBytes(StandardCharsets.UTF_8).length < LONGITUD_MINIMA_SECRETO) {
            throw new IllegalStateException("seguridad.jwt.secreto debe tener al menos " + LONGITUD_MINIMA_SECRETO + " bytes");
        }
        SecretKey clave = new SecretKeySpec(secreto.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(clave));
        this.decoderAcceso = decoder(clave, emisor, TIPO_ACCESO);
        this.decoderA2F = decoder(clave, emisor, TIPO_A2F);
        this.emisor = emisor;
        this.vigenciaAcceso = Duration.ofMinutes(minutosAcceso);
        this.vigenciaA2F = Duration.ofMinutes(minutosA2F);
        this.clock = clock;
    }

    public String emitirAcceso(Integer clienteId, String nombreUsuario) {
        return emitir(clienteId, nombreUsuario, TIPO_ACCESO, vigenciaAcceso);
    }

    public String emitirA2F(Integer clienteId, String nombreUsuario) {
        return emitir(clienteId, nombreUsuario, TIPO_A2F, vigenciaA2F);
    }

    /** Devuelve el id del cliente si el token temporal es valido, vigente y de tipo A2F. */
    public Optional<Integer> validarA2F(String token) {
        try {
            return Optional.of(Integer.valueOf(decoderA2F.decode(token).getSubject()));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** Decoder del resource server: rechaza cualquier token que no sea de ACCESO. */
    public JwtDecoder decoderAcceso() {
        return decoderAcceso;
    }

    public long segundosAcceso() {
        return vigenciaAcceso.toSeconds();
    }

    public long segundosA2F() {
        return vigenciaA2F.toSeconds();
    }

    private String emitir(Integer clienteId, String nombreUsuario, String tipo, Duration vigencia) {
        Instant ahora = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(emisor)
                .subject(String.valueOf(clienteId))
                .issuedAt(ahora)
                .expiresAt(ahora.plus(vigencia))
                .claim(CLAIM_TIPO, tipo)
                .claim(CLAIM_USUARIO, nombreUsuario)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private static JwtDecoder decoder(SecretKey clave, String emisor, String tipoEsperado) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(clave).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<Jwt>(
                JwtValidators.createDefaultWithIssuer(emisor),
                new JwtClaimValidator<String>(CLAIM_TIPO, tipoEsperado::equals)));
        return decoder;
    }
}
