package com.proyecto.servicios.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyecto.servicios.dto.response.ApiResponse;
import com.proyecto.servicios.dto.response.GenericResponse;
import com.proyecto.servicios.seguridad.TokenJwt;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

import java.io.IOException;
import java.time.Clock;

/**
 * Seguridad de la API: sin sesion en servidor (stateless) y con JWT de ACCESO en
 * {@code Authorization: Bearer <token>}. El token temporal del paso A2F no sirve aqui.
 * <p>
 * Publicos: alta de clientes, inicio de sesion, catalogo de productos, Swagger y health.
 * Todo lo demas exige sesion iniciada.
 */
@Configuration
public class SeguridadConfig {

    static final String MSG_SIN_SESION = "Se requiere iniciar sesión. Envíe el token de acceso en el encabezado Authorization: Bearer <token>.";
    static final String MSG_SIN_PERMISO = "No tiene permiso para realizar esta operación.";

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper objectMapper) throws Exception {
        http
                // API sin cookies de sesion: CSRF no aplica
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/v1/clientes").permitAll()
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/productosList").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/actuator/health", "/error").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(o -> o
                        .jwt(jwt -> {
                        })
                        .authenticationEntryPoint((req, res, ex) -> escribir(res, objectMapper, HttpStatus.UNAUTHORIZED, MSG_SIN_SESION)))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> escribir(res, objectMapper, HttpStatus.UNAUTHORIZED, MSG_SIN_SESION))
                        .accessDeniedHandler((req, res, ex) -> escribir(res, objectMapper, HttpStatus.FORBIDDEN, MSG_SIN_PERMISO)));
        return http.build();
    }

    /** Solo acepta tokens de ACCESO firmados por esta API, vigentes y con el emisor configurado. */
    @Bean
    public JwtDecoder jwtDecoder(TokenJwt tokenJwt) {
        return tokenJwt.decoderAcceso();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }

    /** Responde con el mismo formato {codigo, mensaje} que el resto de la API. */
    private static void escribir(HttpServletResponse res, ObjectMapper objectMapper, HttpStatus status, String mensaje)
            throws IOException {
        GenericResponse cuerpo = new GenericResponse();
        cuerpo.setCodigo(ApiResponse.ERROR);
        cuerpo.setMensaje(mensaje);
        res.setStatus(status.value());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(res.getOutputStream(), cuerpo);
    }
}
