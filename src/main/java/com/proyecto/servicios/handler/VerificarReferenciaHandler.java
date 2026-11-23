package com.proyecto.servicios.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyecto.servicios.client.GestoPagoTxGateway;
import com.proyecto.servicios.client.GestoPagoTxGateway.Resultado;
import com.proyecto.servicios.client.RecursosGestoPago;
import com.proyecto.servicios.dto.request.VerificacionReferenciaRequest;
import com.proyecto.servicios.dto.response.ApiResponse;
import com.proyecto.servicios.dto.response.VerificacionReferenciaResponse;
import com.proyecto.servicios.entity.sf.ProductoCatalogo;
import com.proyecto.servicios.entity.sf.VerificacionReferencia;
import com.proyecto.servicios.enums.Catalogos.ResultadoVerificacion;
import com.proyecto.servicios.repository.sf.ProductoCatalogoRepository;
import com.proyecto.servicios.repository.sf.VerificacionReferenciaRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Verifica la referencia de un pago de servicio:
 * <ol>
 *   <li>El producto existe en el catalogo y es de pago de servicios (tipoFront 2 o 4).</li>
 *   <li>La referencia cumple el formato del producto (p. ej. 10 digitos si es un telefono, tipoReferencia "a").</li>
 *   <li>Si GestoPago ofrece verifyReference para el servicio (producto tipoFront 4), se consulta; si no,
 *       la verificacion es solo de formato y asi se informa (FORMATO_VALIDO).</li>
 *   <li>Se guarda con un folio que vence en 15 minutos y sirve para un solo pago.</li>
 * </ol>
 * Cada cliente puede verificar como maximo {@code referencias.max-por-hora} referencias por hora, para no
 * agotar los limites de GestoPago.
 */
@Component
@Slf4j
public class VerificarReferenciaHandler implements Handler<VerificarReferenciaComando, VerificacionReferenciaResponse> {

    static final String MSG_PRODUCTO = "El servicio y producto indicados no están en el catálogo.";
    static final String MSG_NO_ES_SERVICIO = "Este producto no se paga con referencia (es una recarga o no está soportado).";
    static final String MSG_TELEFONO = "La referencia de este servicio es un número de teléfono de 10 dígitos.";
    static final String MSG_LIMITE = "Alcanzó el máximo de verificaciones por hora. Intente más tarde.";
    static final String MSG_SIN_RESPUESTA = "No fue posible verificar la referencia con GestoPago en este momento. Intente más tarde.";
    static final String MSG_SOLO_FORMATO = "Este servicio no ofrece verificación en línea; se validó el formato de la referencia.";
    private static final int TIPO_FRONT_VERIFICACION = 4;
    private static final long MINUTOS_VIGENCIA = 15;

    private final ProductoCatalogoRepository catalogoRepository;
    private final VerificacionReferenciaRepository verificacionRepository;
    private final GestoPagoTxGateway gateway;
    private final RecursosGestoPago recursos;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final int maxPorHora;

    public VerificarReferenciaHandler(ProductoCatalogoRepository catalogoRepository,
                                      VerificacionReferenciaRepository verificacionRepository,
                                      GestoPagoTxGateway gateway,
                                      RecursosGestoPago recursos,
                                      ObjectMapper objectMapper,
                                      Clock clock,
                                      @Value("${referencias.max-por-hora:20}") int maxPorHora) {
        this.catalogoRepository = catalogoRepository;
        this.verificacionRepository = verificacionRepository;
        this.gateway = gateway;
        this.recursos = recursos;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.maxPorHora = maxPorHora;
    }

    @Override
    public ApiResponse<VerificacionReferenciaResponse> handle(VerificarReferenciaComando comando) {
        VerificacionReferenciaRequest request = comando.request();

        Optional<ProductoCatalogo> encontrado =
                catalogoRepository.findFirstByIdServicioAndIdProducto(request.getIdServicio(), request.getIdProducto());
        if (encontrado.isEmpty()) {
            return ApiResponse.error(HttpStatus.NOT_FOUND, MSG_PRODUCTO);
        }
        ProductoCatalogo producto = encontrado.get();
        if (!"PAGO_SERVICIO".equals(RecursosGestoPago.tipoOperacion(producto.getTipoFront()))) {
            return ApiResponse.error(HttpStatus.UNPROCESSABLE_ENTITY, MSG_NO_ES_SERVICIO);
        }
        if ("a".equalsIgnoreCase(producto.getTipoReferencia()) && !request.getReferencia().matches("\\d{10}")) {
            return ApiResponse.error(HttpStatus.BAD_REQUEST, MSG_TELEFONO);
        }
        LocalDateTime ahora = LocalDateTime.now(clock);
        if (verificacionRepository.countByClienteIdAndFechaCreacionAfter(comando.clienteId(), ahora.minusHours(1)) >= maxPorHora) {
            return ApiResponse.error(HttpStatus.TOO_MANY_REQUESTS, MSG_LIMITE);
        }

        // Producto de verificacion: el propio (si es tipoFront 4) o el tipoFront 4 del mismo servicio
        Optional<ProductoCatalogo> deVerificacion = Integer.valueOf(TIPO_FRONT_VERIFICACION).equals(producto.getTipoFront())
                ? Optional.of(producto)
                : catalogoRepository.findFirstByIdServicioAndTipoFront(producto.getIdServicio(), TIPO_FRONT_VERIFICACION);

        VerificacionReferencia verificacion = nueva(comando.clienteId(), request, ahora);
        if (deVerificacion.isEmpty()) {
            verificacion.setResultado(ResultadoVerificacion.FORMATO_VALIDO);
            verificacion.setVerificadaGestoPago(false);
            verificacion.setMensaje(MSG_SOLO_FORMATO);
        } else {
            Resultado resultado = gateway.verificarReferencia(payload(deVerificacion.get(), request.getReferencia()));
            if (!resultado.tieneRespuesta()) {
                // Sin respuesta legible no se puede afirmar nada sobre la referencia
                return ApiResponse.error(HttpStatus.SERVICE_UNAVAILABLE, MSG_SIN_RESPUESTA);
            }
            boolean valida = "01".equals(resultado.codigo());
            verificacion.setResultado(valida ? ResultadoVerificacion.VALIDA : ResultadoVerificacion.INVALIDA);
            verificacion.setVerificadaGestoPago(true);
            verificacion.setMensaje(recortar(resultado.xml().texto()));
            verificacion.setSaldoPendiente(valida ? saldo(resultado) : null);
        }
        verificacionRepository.save(verificacion);
        log.info("Referencia verificada para cliente ID={} servicio={} resultado={}",
                comando.clienteId(), request.getIdServicio(), verificacion.getResultado());

        if (verificacion.getResultado() == ResultadoVerificacion.INVALIDA) {
            return ApiResponse.error(HttpStatus.UNPROCESSABLE_ENTITY, "La referencia no es válida"
                    + (verificacion.getMensaje() != null ? ": " + verificacion.getMensaje() : "."));
        }
        String mensaje = verificacion.getResultado() == ResultadoVerificacion.VALIDA
                ? "Referencia válida." : MSG_SOLO_FORMATO;
        return ApiResponse.exito(HttpStatus.OK, mensaje, aResponse(verificacion, producto));
    }

    private VerificacionReferencia nueva(Integer clienteId, VerificacionReferenciaRequest request, LocalDateTime ahora) {
        VerificacionReferencia v = new VerificacionReferencia();
        v.setFolio(UUID.randomUUID().toString());
        v.setClienteId(clienteId);
        v.setIdServicio(request.getIdServicio());
        v.setIdProducto(request.getIdProducto());
        v.setReferencia(request.getReferencia());
        v.setUsada(false);
        v.setFechaCreacion(ahora);
        v.setExpira(ahora.plusMinutes(MINUTOS_VIGENCIA));
        return v;
    }

    private String payload(ProductoCatalogo deVerificacion, String referencia) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("idProducto", deVerificacion.getIdProducto());
        payload.put("idServicio", deVerificacion.getIdServicio());
        payload.put("referencia", referencia);
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No fue posible armar el payload de verifyReference", e);
        }
    }

    private static BigDecimal saldo(Resultado resultado) {
        String texto = resultado.xml().getMensaje() != null ? resultado.xml().getMensaje().getSaldo() : null;
        if (texto == null || texto.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(texto.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String recortar(String texto) {
        if (texto == null) {
            return null;
        }
        String limpio = texto.trim();
        return limpio.length() > 500 ? limpio.substring(0, 500) : limpio;
    }

    private VerificacionReferenciaResponse aResponse(VerificacionReferencia v, ProductoCatalogo producto) {
        VerificacionReferenciaResponse res = new VerificacionReferenciaResponse();
        res.setFolio(v.getFolio());
        res.setResultado(v.getResultado());
        res.setVerificadaConGestoPago(v.getVerificadaGestoPago());
        res.setMensaje(v.getMensaje());
        res.setSaldoPendiente(v.getSaldoPendiente());
        res.setIdServicio(v.getIdServicio());
        res.setIdProducto(v.getIdProducto());
        res.setServicio(producto.getServicio());
        res.setProducto(producto.getProducto());
        res.setReferencia(v.getReferencia());
        res.setLogoUrl(recursos.logoUrl(v.getIdServicio()));
        res.setExpira(v.getExpira());
        return res;
    }
}
