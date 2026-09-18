package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.client.ProductoClient;
import com.proyecto.servicios.config.exception.IntegracionException;
import com.proyecto.servicios.entity.gestopago.GestoPagoToken;
import com.proyecto.servicios.model.producto.ProductoDto;
import com.proyecto.servicios.model.producto.ProductoListResponse;
import com.proyecto.servicios.model.producto.ProductoXml;
import com.proyecto.servicios.repositorys.gestopago.GestoPagoTokenRepository;
import com.proyecto.servicios.service.GestoPagoTokenService;
import com.proyecto.servicios.service.ProductoService;
import feign.FeignException;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
@Slf4j
public class ProductoServiceImpl implements ProductoService {

    private final ProductoClient productoClient;
    private final GestoPagoTokenRepository tokenRepository;
    private final GestoPagoTokenService tokenService;

    @Value("${productos.service.id-distribuidor}")
    private Integer idDistribuidor;

    @Value("${productos.service.codigo-dispositivo}")
    private String codigoDispositivo;

    // Cache en memoria para evitar llamadas repetidas a la API
    private final List<ProductoDto> productosCache = new CopyOnWriteArrayList<>();

    public ProductoServiceImpl(ProductoClient productoClient,
                               GestoPagoTokenRepository tokenRepository,
                               GestoPagoTokenService tokenService) {
        this.productoClient = productoClient;
        this.tokenRepository = tokenRepository;
        this.tokenService = tokenService;
    }

    @Override
    public List<ProductoDto> obtenerProductos() {
        if (productosCache.isEmpty()) {
            log.warn("Cache de productos vacio. Intentando actualizar desde API...");
            actualizarProductosDesdeApi();
        }
        return new ArrayList<>(productosCache);
    }

    @Override
    @Scheduled(cron = "${productos.service.cron}")
    public void actualizarProductosDesdeApi() {
        log.info("Iniciando actualizacion de lista de productos desde PuntoRed. Distribuidor={}", idDistribuidor);

        String token = obtenerTokenActivo();
        String xmlRespuesta = llamarApi(token);
        List<ProductoDto> productos = parsearXml(xmlRespuesta);

        productosCache.clear();
        productosCache.addAll(productos);

        log.info("Actualizacion de productos finalizada. Total productos obtenidos: {}", productos.size());
    }

    // ─── Metodos privados ────────────────────────────────────────────────────

    private String obtenerTokenActivo() {
        Optional<GestoPagoToken> tokenOpt =
                tokenRepository.findByIdDistribuidorAndCodigoDispositivo(idDistribuidor, codigoDispositivo);

        if (tokenOpt.isEmpty()) {
            log.warn("No se encontro token en BD. Disparando renovacion automatica contra PuntoRed...");
            tokenService.renovarToken();
            tokenOpt = tokenRepository.findByIdDistribuidorAndCodigoDispositivo(idDistribuidor, codigoDispositivo);
        }

        if (tokenOpt.isEmpty()) {
            log.error("No se pudo obtener token activo en BD tras intento de renovacion para distribuidor={}", idDistribuidor);
            throw new IntegracionException(
                    "No hay token activo disponible. Verifique la conexion y credenciales de PuntoRed.",
                    HttpStatus.SERVICE_UNAVAILABLE
            );
        }
        return tokenOpt.get().getToken();
    }

    private String llamarApi(String token) {
        try {
            String authHeader = formatBearerToken(token);
            log.info("Llamando a PuntoRed getProductList con token dinámico...");
            String respuesta = productoClient.getProductList(authHeader);
            log.info("Respuesta recibida exitosamente de PuntoRed getProductList");
            return respuesta;
        } catch (FeignException.Unauthorized | FeignException.Forbidden ex) {
            log.warn("Token rechazado por PuntoRed (401/403). Forzando renovacion de token en tiempo real...");
            tokenService.renovarToken();
            String nuevoToken = tokenRepository
                    .findByIdDistribuidorAndCodigoDispositivo(idDistribuidor, codigoDispositivo)
                    .map(GestoPagoToken::getToken)
                    .orElse(token);

            try {
                String authRetry = formatBearerToken(nuevoToken);
                log.info("Reintentando llamada a PuntoRed getProductList con token recien renovado...");
                return productoClient.getProductList(authRetry);
            } catch (Exception retryEx) {
                log.error("Fallo el reintento con token renovado: {}", retryEx.getMessage());
                throw new IntegracionException(
                        "Token no valido o expirado. No fue posible reautenticar con PuntoRed.",
                        HttpStatus.UNAUTHORIZED,
                        retryEx
                );
            }
        } catch (FeignException.GatewayTimeout ex) {
            log.error("Timeout al llamar PuntoRed getProductList");
            throw new IntegracionException(
                    "Timeout al conectar con PuntoRed. Intente nuevamente.",
                    HttpStatus.GATEWAY_TIMEOUT,
                    ex
            );
        } catch (FeignException ex) {
            log.error("Error de comunicacion con PuntoRed getProductList. Status: {}", ex.status());
            throw new IntegracionException(
                    "Error de comunicacion con el servicio de productos.",
                    HttpStatus.BAD_GATEWAY,
                    ex
            );
        }
    }

    private List<ProductoDto> parsearXml(String xml) {
        try {
            JAXBContext context = JAXBContext.newInstance(ProductoListResponse.class);
            ProductoListResponse response = (ProductoListResponse) context.createUnmarshaller()
                    .unmarshal(new StringReader(xml));

            if (response.getMensaje() == null || !"01".equals(response.getMensaje().getCodigo())) {
                String texto = response.getMensaje() != null ? response.getMensaje().getTexto() : "Respuesta invalida";
                log.error("PuntoRed retorno codigo de error: {}", texto);
                throw new IntegracionException("El servicio de productos retorno un error: " + texto);
            }

            return response.getListaProductos().stream()
                    .map(this::mapearADto)
                    .toList();

        } catch (JAXBException ex) {
            log.error("Error al parsear XML de productos: {}", ex.getMessage());
            throw new IntegracionException("Error al procesar la respuesta del servicio de productos.", ex);
        }
    }

    private ProductoDto mapearADto(ProductoXml xml) {
        ProductoDto dto = new ProductoDto();
        dto.setIdServicio(xml.getIdServicio());
        dto.setIdProducto(xml.getIdProducto());
        dto.setIdCatTipoServicio(xml.getIdCatTipoServicio());
        dto.setServicio(xml.getServicio());
        dto.setProducto(xml.getProducto());
        dto.setPrecio(xml.getPrecio());
        dto.setTipoFront(xml.getTipoFront());
        dto.setHasDigitoVerificador(xml.getHasDigitoVerificador());
        dto.setShowAyuda(xml.getShowAyuda());
        dto.setTipoReferencia(xml.getTipoReferencia());
        dto.setLegend(xml.getLegend());
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
