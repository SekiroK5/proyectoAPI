package com.proyecto.servicios.service.impl;

import com.proyecto.servicios.client.CircuitoGestoPago;
import com.proyecto.servicios.client.LimiteLlamadasGestoPago;
import com.proyecto.servicios.client.ProductoClient;
import com.proyecto.servicios.client.RecursosGestoPago;
import org.springframework.web.server.ResponseStatusException;
import com.proyecto.servicios.entity.gestopago.GestoPagoToken;
import com.proyecto.servicios.entity.sf.ProductoCatalogo;
import com.proyecto.servicios.model.producto.ProductoDto;
import com.proyecto.servicios.model.producto.ProductoListResponse;
import com.proyecto.servicios.model.producto.ProductoXml;
import com.proyecto.servicios.repository.gestopago.GestoPagoTokenRepository;
import com.proyecto.servicios.service.GestoPagoTokenService;
import com.proyecto.servicios.service.ProductoService;
import feign.FeignException;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.StringReader;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Catalogo de GestoPago. La documentacion de PuntoRed indica que getProductList se usa para guardar el
 * catalogo en la base de datos del integrador, como maximo 3 veces al dia (si no, bloquean la IP), y que
 * nunca debe servir directamente al frontend. Por eso:
 * <ul>
 *   <li>{@link #obtenerProductos()} lee solo de la base de datos; ni /productosList ni los pagos llaman a GestoPago.</li>
 *   <li>Solo la tarea diaria (y el arranque, si el catalogo esta vacio) llama a getProductList, dentro de un
 *       cupo diario contado en la base de datos y nunca mientras las llamadas esten en pausa.</li>
 * </ul>
 */
@Service
@Slf4j
public class ProductoServiceImpl implements ProductoService {

    static final String OPERACION = "getProductList";

    private final ProductoClient productoClient;
    private final GestoPagoTokenRepository tokenRepository;
    private final GestoPagoTokenService tokenService;
    private final CatalogoProductos catalogo;
    private final LimiteLlamadasGestoPago limite;
    private final CircuitoGestoPago circuito;
    private final RecursosGestoPago recursos;

    @Value("${productos.service.id-distribuidor}")
    private Integer idDistribuidor;

    @Value("${productos.service.codigo-dispositivo}")
    private String codigoDispositivo;

    /** PuntoRed permite 3 al dia; se deja 1 de margen. */
    @Value("${productos.service.max-llamadas-diarias:2}")
    private int maxLlamadasDiarias = 2;

    public ProductoServiceImpl(ProductoClient productoClient,
                               GestoPagoTokenRepository tokenRepository,
                               GestoPagoTokenService tokenService,
                               CatalogoProductos catalogo,
                               LimiteLlamadasGestoPago limite,
                               CircuitoGestoPago circuito,
                               RecursosGestoPago recursos) {
        this.productoClient = productoClient;
        this.tokenRepository = tokenRepository;
        this.tokenService = tokenService;
        this.catalogo = catalogo;
        this.limite = limite;
        this.circuito = circuito;
        this.recursos = recursos;
    }

    @Override
    public List<ProductoDto> obtenerProductos() {
        List<ProductoCatalogo> productos = catalogo.listar();
        // Servicios que tienen un producto de verificacion (tipoFront 4) en el catalogo
        Set<Integer> verificables = new HashSet<>();
        for (ProductoCatalogo p : productos) {
            if (Integer.valueOf(4).equals(p.getTipoFront())) {
                verificables.add(p.getIdServicio());
            }
        }
        return productos.stream().map(p -> aDto(p, verificables.contains(p.getIdServicio()))).toList();
    }

    /** Al arrancar solo se pide el catalogo si la base de datos aun no lo tiene. */
    @EventListener(ApplicationReadyEvent.class)
    public void cargarSiVacio() {
        if (!catalogo.estaVacio()) {
            return;
        }
        log.info("Catalogo de productos vacio al arrancar; se intenta cargar una vez desde PuntoRed");
        try {
            actualizarProductosDesdeApi();
        } catch (RuntimeException ex) {
            log.error("No se pudo cargar el catalogo al arrancar: {}", ex.getClass().getSimpleName());
        }
    }

    @Override
    @Scheduled(cron = "${productos.service.cron}")
    public synchronized void actualizarProductosDesdeApi() {
        if (!circuito.permite()) {
            log.warn("Actualizacion del catalogo omitida: llamadas a GestoPago en pausa por fallas consecutivas");
            return;
        }
        if (!limite.reservar(OPERACION, maxLlamadasDiarias)) {
            return;
        }
        log.info("Iniciando actualizacion de lista de productos desde PuntoRed. Distribuidor={}", idDistribuidor);

        String token = obtenerTokenActivo();
        String xmlRespuesta = llamarApi(token);
        List<ProductoXml> productos = parsearXml(xmlRespuesta);
        circuito.registrarExito();

        // Sin duplicados (idServicio + idProducto es unico en la tabla); se queda el primero que llega
        Map<String, ProductoCatalogo> unicos = new LinkedHashMap<>();
        for (ProductoXml xml : productos) {
            unicos.putIfAbsent(xml.getIdServicio() + ":" + xml.getIdProducto(), aEntidad(xml));
        }
        catalogo.reemplazar(List.copyOf(unicos.values()));
        log.info("Actualizacion de productos finalizada. Total productos guardados: {}", unicos.size());
    }

    // ─── Metodos privados ────────────────────────────────────────────────────

    private String obtenerTokenActivo() {
        Optional<GestoPagoToken> tokenOpt =
                tokenRepository.findByIdDistribuidorAndCodigoDispositivo(idDistribuidor, codigoDispositivo);

        if (tokenOpt.isEmpty()) {
            log.warn("No se encontro token en BD. Se intenta una renovacion contra PuntoRed...");
            tokenService.renovarToken();
            tokenOpt = tokenRepository.findByIdDistribuidorAndCodigoDispositivo(idDistribuidor, codigoDispositivo);
        }

        if (tokenOpt.isEmpty()) {
            log.error("No se pudo obtener token activo en BD tras intento de renovacion para distribuidor={}", idDistribuidor);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "No hay token activo disponible. Verifique la conexion y credenciales de PuntoRed.");
        }
        return tokenOpt.get().getToken();
    }

    private String llamarApi(String token) {
        try {
            String authHeader = formatBearerToken(token);
            log.info("Llamando a PuntoRed getProductList...");
            String respuesta = productoClient.getProductList(authHeader);
            log.info("Respuesta recibida exitosamente de PuntoRed getProductList");
            return respuesta;
        } catch (FeignException.Unauthorized | FeignException.Forbidden ex) {
            log.warn("Token rechazado por PuntoRed (401/403) en getProductList");
            // Sin token nuevo no se reintenta: repetir con el token rechazado solo gasta cupo
            if (!tokenService.renovarToken()) {
                circuito.registrarFalla(OPERACION);
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Token no valido o expirado. No fue posible reautenticar con PuntoRed.", ex);
            }
            String nuevoToken = tokenRepository
                    .findByIdDistribuidorAndCodigoDispositivo(idDistribuidor, codigoDispositivo)
                    .map(GestoPagoToken::getToken)
                    .orElse(token);
            try {
                log.info("Reintentando getProductList con token recien renovado...");
                return productoClient.getProductList(formatBearerToken(nuevoToken));
            } catch (RuntimeException retryEx) {
                log.error("Fallo el reintento con token renovado: {}", retryEx.getClass().getSimpleName());
                circuito.registrarFalla(OPERACION);
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Token no valido o expirado. No fue posible reautenticar con PuntoRed.", retryEx);
            }
        } catch (FeignException.GatewayTimeout ex) {
            log.error("Timeout al llamar PuntoRed getProductList");
            circuito.registrarFalla(OPERACION);
            throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT,
                    "Timeout al conectar con PuntoRed. Intente nuevamente.", ex);
        } catch (FeignException ex) {
            log.error("Error de comunicacion con PuntoRed getProductList. Status: {}", ex.status());
            circuito.registrarFalla(OPERACION);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Error de comunicacion con el servicio de productos.", ex);
        }
    }

    private List<ProductoXml> parsearXml(String xml) {
        try {
            JAXBContext context = JAXBContext.newInstance(ProductoListResponse.class);
            ProductoListResponse response = (ProductoListResponse) context.createUnmarshaller()
                    .unmarshal(new StringReader(xml));

            if (response.getMensaje() == null || !"01".equals(response.getMensaje().getCodigo())) {
                String texto = response.getMensaje() != null ? response.getMensaje().getTexto() : "Respuesta invalida";
                log.error("PuntoRed retorno codigo de error: {}", texto);
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "El servicio de productos retorno un error: " + texto);
            }
            return response.getListaProductos();

        } catch (JAXBException ex) {
            log.error("Error al parsear XML de productos: {}", ex.getMessage());
            circuito.registrarFalla(OPERACION);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Error al procesar la respuesta del servicio de productos.", ex);
        }
    }

    private static ProductoCatalogo aEntidad(ProductoXml xml) {
        ProductoCatalogo p = new ProductoCatalogo();
        p.setIdServicio(xml.getIdServicio());
        p.setIdProducto(xml.getIdProducto());
        p.setIdCatTipoServicio(xml.getIdCatTipoServicio());
        p.setServicio(xml.getServicio());
        p.setProducto(xml.getProducto());
        p.setPrecio(xml.getPrecio() != null ? BigDecimal.valueOf(xml.getPrecio()) : null);
        p.setTipoFront(xml.getTipoFront());
        p.setHasDigitoVerificador(xml.getHasDigitoVerificador());
        p.setShowAyuda(xml.getShowAyuda());
        p.setTipoReferencia(xml.getTipoReferencia());
        p.setLegend(xml.getLegend());
        return p;
    }

    private ProductoDto aDto(ProductoCatalogo p, boolean verificable) {
        ProductoDto dto = new ProductoDto();
        dto.setIdServicio(p.getIdServicio());
        dto.setIdProducto(p.getIdProducto());
        dto.setIdCatTipoServicio(p.getIdCatTipoServicio());
        dto.setServicio(p.getServicio());
        dto.setProducto(p.getProducto());
        dto.setPrecio(p.getPrecio() != null ? p.getPrecio().doubleValue() : null);
        dto.setTipoFront(p.getTipoFront());
        dto.setHasDigitoVerificador(p.getHasDigitoVerificador());
        dto.setShowAyuda(p.getShowAyuda());
        dto.setTipoReferencia(p.getTipoReferencia());
        dto.setLegend(p.getLegend());
        dto.setLogoUrl(recursos.logoUrl(p.getIdServicio()));
        dto.setCategoria(recursos.categoria(p.getIdCatTipoServicio()));
        String tipoOperacion = RecursosGestoPago.tipoOperacion(p.getTipoFront());
        dto.setTipoOperacion(tipoOperacion);
        boolean esServicio = "PAGO_SERVICIO".equals(tipoOperacion);
        dto.setRequiereVerificacion(esServicio);
        dto.setVerificableEnGestoPago(esServicio && verificable);
        if (esServicio) {
            dto.setImagenReferenciaUrl(recursos.imagenReferenciaUrl(p.getIdServicio()));
        }
        return dto;
    }

    private String formatBearerToken(String token) {
        if (token == null || token.isBlank()) {
            return "";
        }
        String trimmed = token.trim();
        return trimmed.startsWith("Bearer ") ? trimmed : "Bearer " + trimmed;
    }
}
