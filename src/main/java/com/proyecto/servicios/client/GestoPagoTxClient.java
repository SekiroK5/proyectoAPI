package com.proyecto.servicios.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.Map;

/**
 * Transacciones de GestoPago/PuntoRed. Ambos metodos reciben un formulario urlencoded con:
 * <ul>
 *   <li>{@code random}: texto aleatorio, normalmente el timestamp actual.</li>
 *   <li>{@code signed}: el JSON del payload cifrado con AES-256-CBC (ver CifradoGestoPago).</li>
 * </ul>
 * y responden XML. El timeout de lectura se configura arriba de 62 s, que es lo que GestoPago espera
 * antes de responder codigo 82.
 */
@FeignClient(name = "gestoPagoTx", url = "${gestopago.tx.url}")
public interface GestoPagoTxClient {

    @PostMapping(value = "/sistema/service/sendTx.do",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            headers = "Accept=text/xml, application/xml, */*")
    String sendTx(@RequestHeader("Authorization") String authorization,
                  @RequestHeader(value = "X-API-Key", required = false) String apiKey,
                  @RequestBody Map<String, ?> formulario);

    /**
     * Estado de una transaccion cuya respuesta no llego. Se envian exactamente los mismos parametros
     * que en sendTx, no antes de 62 s despues y no despues de 24 h.
     */
    @PostMapping(value = "/sistema/service/confirmTx.do",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            headers = "Accept=text/xml, application/xml, */*")
    String confirmTx(@RequestHeader("Authorization") String authorization,
                     @RequestHeader(value = "X-API-Key", required = false) String apiKey,
                     @RequestBody Map<String, ?> formulario);
}
