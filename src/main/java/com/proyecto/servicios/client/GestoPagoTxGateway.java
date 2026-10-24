package com.proyecto.servicios.client;

import com.proyecto.servicios.model.gestopago.SendTxResponseXml;
import com.proyecto.servicios.seguridad.CifradoGestoPago;
import com.proyecto.servicios.service.GestoPagoTokenService;
import feign.FeignException;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.StringReader;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Pasos 4 a 6 del flujo de pago: cifra el payload, obtiene el token vigente y envia el formulario
 * {@code random + signed} a GestoPago. Nunca lanza excepciones: cualquier caso en el que no se sepa si
 * GestoPago aplico la operacion se devuelve como {@link Resultado#sinRespuesta}, para confirmarla despues.
 */
@Component
@Slf4j
public class GestoPagoTxGateway {

    private final GestoPagoTxClient txClient;
    private final GestoPagoTokenService tokenService;
    private final CifradoGestoPago cifrado;
    private final Clock clock;
    private final String apiKey;
    private final JAXBContext contextoXml;

    public GestoPagoTxGateway(GestoPagoTxClient txClient,
                              GestoPagoTokenService tokenService,
                              CifradoGestoPago cifrado,
                              Clock clock,
                              @Value("${gestopago.api-key:}") String apiKey) throws JAXBException {
        this.txClient = txClient;
        this.tokenService = tokenService;
        this.cifrado = cifrado;
        this.clock = clock;
        this.apiKey = apiKey == null || apiKey.isBlank() ? null : apiKey;
        this.contextoXml = JAXBContext.newInstance(SendTxResponseXml.class);
    }

    /**
     * Respuesta de GestoPago, o la razon por la que no se obtuvo.
     *
     * @param enviada false solo cuando la peticion nunca salio (por ejemplo, sin token): la operacion
     *                definitivamente no se aplico. Si es true y no hay xml, el resultado es desconocido.
     */
    public record Resultado(SendTxResponseXml xml, String cuerpo, String error, boolean enviada) {

        static Resultado respuesta(SendTxResponseXml xml, String cuerpo) {
            return new Resultado(xml, cuerpo, null, true);
        }

        static Resultado sinRespuesta(String cuerpo, String error) {
            return new Resultado(null, cuerpo, error, true);
        }

        static Resultado noEnviada(String error) {
            return new Resultado(null, null, error, false);
        }

        /** Hubo respuesta legible de GestoPago. */
        public boolean tieneRespuesta() {
            return xml != null;
        }

        public String codigo() {
            return xml != null ? xml.codigo() : null;
        }
    }

    public Resultado enviar(String payloadJson) {
        return ejecutar("sendTx", payloadJson, formulario -> txClient.sendTx(token(), apiKey, formulario),
                formulario -> txClient.sendTx(tokenRenovado(), apiKey, formulario));
    }

    public Resultado confirmar(String payloadJson) {
        return ejecutar("confirmTx", payloadJson, formulario -> txClient.confirmTx(token(), apiKey, formulario),
                formulario -> txClient.confirmTx(tokenRenovado(), apiKey, formulario));
    }

    private Resultado ejecutar(String metodo, String payloadJson,
                               Function<Map<String, String>, String> llamada,
                               Function<Map<String, String>, String> reintentoConTokenNuevo) {
        Map<String, String> formulario = new LinkedHashMap<>();
        formulario.put("random", String.valueOf(clock.millis()));
        try {
            formulario.put("signed", cifrado.cifrar(payloadJson));
        } catch (IllegalStateException ex) {
            // Sin llave/IV o error al cifrar: no se envia nada a GestoPago
            log.error("GestoPago {} no se envio: {}", metodo, ex.getMessage());
            return Resultado.noEnviada(ex.getMessage());
        }
        try {
            String cuerpo;
            try {
                cuerpo = llamada.apply(formulario);
            } catch (FeignException.Unauthorized | FeignException.Forbidden ex) {
                // 401/403 significa que GestoPago rechazo el token sin procesar la operacion: es seguro reintentar
                log.warn("GestoPago {} rechazo el token ({}). Se renueva y se reintenta una vez", metodo, ex.status());
                cuerpo = reintentoConTokenNuevo.apply(formulario);
            }
            return interpretar(metodo, cuerpo);
        } catch (TokenNoDisponibleException ex) {
            // No se llego a enviar nada a GestoPago
            log.error("GestoPago {} no se envio: {}", metodo, ex.getMessage());
            return Resultado.noEnviada(ex.getMessage());
        } catch (FeignException ex) {
            log.error("GestoPago {} respondio HTTP {}", metodo, ex.status());
            return Resultado.sinRespuesta(ex.contentUTF8(), "HTTP " + ex.status() + " de GestoPago");
        } catch (RuntimeException ex) {
            // Timeout o desconexion: no se sabe si se aplico
            log.error("Sin respuesta de GestoPago {}: {}", metodo, ex.getMessage());
            return Resultado.sinRespuesta(null, "Sin respuesta de GestoPago: " + ex.getClass().getSimpleName());
        }
    }

    private Resultado interpretar(String metodo, String cuerpo) {
        if (cuerpo == null || cuerpo.isBlank()) {
            return Resultado.sinRespuesta(cuerpo, "Respuesta vacia de GestoPago");
        }
        try {
            SendTxResponseXml xml = (SendTxResponseXml) contextoXml.createUnmarshaller().unmarshal(new StringReader(cuerpo));
            log.info("GestoPago {} respondio codigo={} texto={}", metodo, xml.codigo(), xml.texto());
            return Resultado.respuesta(xml, cuerpo);
        } catch (JAXBException ex) {
            log.error("No se pudo leer el XML de GestoPago {}: {}", metodo, ex.getMessage());
            return Resultado.sinRespuesta(cuerpo, "Respuesta de GestoPago no reconocida");
        }
    }

    private String token() {
        return bearer(tokenService.obtenerTokenVigente());
    }

    private String tokenRenovado() {
        tokenService.renovarToken();
        return bearer(tokenService.obtenerTokenVigente());
    }

    private static String bearer(Optional<String> token) {
        String valor = token.filter(t -> !t.isBlank())
                .orElseThrow(() -> new TokenNoDisponibleException("No hay token vigente de GestoPago"))
                .trim();
        return valor.startsWith("Bearer ") ? valor : "Bearer " + valor;
    }

    /** Sin token no se envio nada: la operacion definitivamente no se aplico. */
    static class TokenNoDisponibleException extends RuntimeException {
        TokenNoDisponibleException(String mensaje) {
            super(mensaje);
        }
    }
}
