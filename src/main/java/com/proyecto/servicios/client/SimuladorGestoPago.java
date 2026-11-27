package com.proyecto.servicios.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Modo simulacion de GestoPago ({@code GESTOPAGO_SIMULADO=true}) para probar la plataforma cuando la IP
 * no esta autorizada por PuntoRed. Responde con el mismo XML documentado para sendTx, confirmTx y
 * verifyReference, asi que el resto del flujo (bitacora, saldo, reintentos) es el mismo que con el real.
 * <p>
 * Reglas, segun el ultimo digito del telefono (recargas) o de la referencia (servicios):
 * <ul>
 *   <li>Termina en 0: rechazada (codigo 70, "La transaccion no fue aplicada").</li>
 *   <li>Termina en 9: sin respuesta a tiempo (codigo 82); confirmTx la confirma despues (codigo 06).</li>
 *   <li>Cualquier otro: exitosa (codigo 01) con folio; PINes y tarjetas de regalo reciben un PIN.</li>
 *   <li>verifyReference: una referencia que termina en 0 es invalida (02); las demas son validas (01).</li>
 * </ul>
 */
@Component
@Slf4j
public class SimuladorGestoPago {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MMM/yyyy HH:mm:ss", Locale.forLanguageTag("es-MX"));
    private static final String TELEFONO_CANJEABLES = "#1111111111";

    private final boolean activo;
    private final Clock clock;
    private final ObjectMapper json = new ObjectMapper();
    private final AtomicLong secuencia;

    public SimuladorGestoPago(@Value("${gestopago.simulado:false}") boolean activo, Clock clock) {
        this.activo = activo;
        this.clock = clock;
        // Folios distintos aunque se reinicie la API: arranca desde la hora actual (9 digitos)
        this.secuencia = new AtomicLong(100_000_000L + (clock.millis() / 1000) % 800_000_000L);
        if (activo) {
            log.warn("GestoPago en MODO SIMULACION: los pagos NO se envian a PuntoRed");
        }
    }

    public boolean activo() {
        return activo;
    }

    /** Respuesta simulada para el metodo de GestoPago con el payload JSON (sin cifrar) que se iba a enviar. */
    public String responder(String metodo, String payloadJson) {
        JsonNode p = leer(payloadJson);
        return switch (metodo) {
            case "verifyReference" -> verificarReferencia(texto(p, "referencia"));
            case "confirmTx" -> confirmar();
            default -> enviar(p);
        };
    }

    private String enviar(JsonNode p) {
        String referencia = texto(p, "referencia");
        String telefono = texto(p, "telefono");
        String clave = referencia.isEmpty() ? telefono : referencia;
        if (clave.endsWith("0")) {
            return mensaje("-1", "70", "La transaccion no fue aplicada", null);
        }
        if (clave.endsWith("9")) {
            return mensaje("-1", "82", "Tiempo de espera alcanzado, la transaccion no ha sido registrada", null);
        }
        long folio = secuencia.incrementAndGet();
        String monto = p.hasNonNull("montoPago") ? new BigDecimal(p.get("montoPago").asText()).setScale(2, RoundingMode.HALF_UP).toPlainString() : "0.00";
        String extra = TELEFONO_CANJEABLES.equals(telefono)
                ? "<PIN>SIM-" + Long.toString(folio, 36).toUpperCase(Locale.ROOT) + "</PIN><legend><![CDATA[PIN de prueba generado en modo simulacion.]]></legend>"
                : "";
        return """
                <?xml version='1.0' encoding='UTF-8'?>
                <RESPONSE>
                    <ID_TX>%d</ID_TX>
                    <NUM_AUTORIZACION>%d</NUM_AUTORIZACION>
                    <SALDO>150000.00</SALDO>
                    <COMISION>0.0</COMISION>
                    <FECHA>%s</FECHA>
                    <MONTO>%s</MONTO>
                    <MENSAJE>
                        <CODIGO>01</CODIGO>
                        %s
                        <TEXTO>Operacion realizada con exito (simulada)</TEXTO>
                        <REFERENCIA>%s</REFERENCIA>
                    </MENSAJE>
                </RESPONSE>
                """.formatted(folio + 190_000_000L, folio, FECHA.format(LocalDateTime.now(clock)), monto, extra, escapar(referencia));
    }

    private String confirmar() {
        long folio = secuencia.incrementAndGet();
        return mensaje(String.valueOf(folio), "06",
                "La transaccion fue aplicada exitosamente. Autorizacion [" + folio + "] (simulada)", String.valueOf(folio + 190_000_000L));
    }

    private String verificarReferencia(String referencia) {
        if (referencia.isEmpty() || referencia.endsWith("0")) {
            return "<?xml version='1.0' encoding='UTF-8'?><RESPONSE><MENSAJE><CODIGO>02</CODIGO>"
                    + "<TEXTO>Referencia Invalida</TEXTO></MENSAJE></RESPONSE>";
        }
        // Adeudo "realista" que depende de la referencia, para que siempre sea el mismo
        int suma = referencia.chars().map(c -> Character.isDigit(c) ? c - '0' : 1).sum();
        BigDecimal adeudo = BigDecimal.valueOf(150 + (suma * 37L) % 1800).add(new BigDecimal("0.50"));
        return "<?xml version='1.0' encoding='UTF-8'?><RESPONSE><MENSAJE><CODIGO>01</CODIGO>"
                + "<TEXTO>Referencia Valida (simulada)</TEXTO><SALDO>" + adeudo.toPlainString() + "</SALDO></MENSAJE></RESPONSE>";
    }

    private static String mensaje(String autorizacion, String codigo, String texto, String idTx) {
        return "<?xml version='1.0' encoding='UTF-8'?><RESPONSE><NUM_AUTORIZACION>" + autorizacion + "</NUM_AUTORIZACION>"
                + "<MENSAJE><CODIGO>" + codigo + "</CODIGO><TEXTO>" + texto + "</TEXTO>"
                + (idTx != null ? "<ID_TX>" + idTx + "</ID_TX>" : "")
                + "</MENSAJE></RESPONSE>";
    }

    private JsonNode leer(String payloadJson) {
        try {
            return json.readTree(payloadJson == null ? "{}" : payloadJson);
        } catch (Exception ex) {
            return json.createObjectNode();
        }
    }

    private static String texto(JsonNode p, String campo) {
        return p.hasNonNull(campo) ? p.get(campo).asText().trim() : "";
    }

    private static String escapar(String valor) {
        return valor.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
