package com.proyecto.servicios.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyecto.servicios.client.GestoPagoTxGateway;
import com.proyecto.servicios.client.GestoPagoTxGateway.Resultado;
import com.proyecto.servicios.dto.request.TransaccionRequest;
import com.proyecto.servicios.dto.response.ApiResponse;
import com.proyecto.servicios.dto.response.TransaccionResponse;
import com.proyecto.servicios.entity.sf.Cliente;
import com.proyecto.servicios.entity.sf.Cuenta;
import com.proyecto.servicios.entity.sf.Transaccion;
import com.proyecto.servicios.enums.Catalogos.EstatusCuenta;
import com.proyecto.servicios.enums.Catalogos.EstatusTransaccion;
import com.proyecto.servicios.model.producto.ProductoDto;
import com.proyecto.servicios.repository.sf.CuentaRepository;
import com.proyecto.servicios.repository.sf.VerificacionReferenciaRepository;
import com.proyecto.servicios.entity.sf.VerificacionReferencia;
import com.proyecto.servicios.enums.Catalogos.ResultadoVerificacion;
import com.proyecto.servicios.mapper.TransaccionMapper;
import com.proyecto.servicios.repository.sf.TransaccionRepository;
import com.proyecto.servicios.seguridad.ValidadorA2F;
import com.proyecto.servicios.service.ProductoService;
import com.proyecto.servicios.service.impl.RegistroTransacciones;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Flujo de pago con GestoPago (sendTx):
 * <ol>
 *   <li>Validaciones previas: la cuenta es del usuario de la sesion, cliente y cuenta activos, saldo
 *       suficiente, producto del catalogo con sus reglas de tipoFront y codigo 2FA.</li>
 *   <li>Arma el payload SendTx (idProducto, idServicio, telefono/referencia, horaLocal, montoPago, upc, unidad).</li>
 *   <li>Aparta el saldo, cifra el payload, obtiene el token y envia la transaccion (GestoPagoTxGateway).</li>
 *   <li>Segun la respuesta: aplica el cargo (codigo 01), devuelve el saldo (rechazo) o lo deja apartado
 *       para confirmarla con confirmTx (sin respuesta o codigo 82).</li>
 * </ol>
 */
@Component
@Slf4j
public class SendTxHandler implements Handler<SendTxComando, TransaccionResponse> {

    static final String CODIGO_EXITO = "01";
    static final String CODIGO_TIMEOUT = "82";
    /** Telefono fijo que exige PuntoRed segun el tipo de producto. */
    static final String TELEFONO_SERVICIOS = "1111111111";
    static final String TELEFONO_CANJEABLES = "#1111111111";

    static final String MSG_CUENTA_NO_EXISTE = "No existe una cuenta con el número proporcionado.";
    static final String MSG_CUENTA_AJENA = "La cuenta no pertenece al usuario de la sesión.";
    static final String MSG_INACTIVO = "El cliente o la cuenta no están activos.";
    static final String MSG_SALDO = "Saldo insuficiente para realizar la operación.";
    static final String MSG_PRODUCTO = "El servicio y producto indicados no están en el catálogo de GestoPago.";
    static final String MSG_A2F_SIN_CONFIGURAR = ValidadorA2F.MSG_SIN_CONFIGURAR;
    static final String MSG_A2F_INVALIDO = ValidadorA2F.MSG_INVALIDO;
    static final String MSG_A2F_EN_USO = ValidadorA2F.MSG_EN_USO;
    static final String MSG_PAGO_EXITOSO = "Pago exitoso.";
    static final String MSG_SIN_VERIFICAR = "Verifique la referencia antes de pagar (POST /api/v1/referencias/verificacion).";
    static final String MSG_VERIFICACION_INVALIDA = "La verificación de la referencia no corresponde a este pago, ya venció o la referencia no es válida. Verifíquela de nuevo.";
    static final String MSG_VERIFICACION_USADA = "Esa verificación de referencia ya se usó en otro pago. Verifíquela de nuevo.";
    static final String MSG_POR_CONFIRMAR = "GestoPago no respondió a tiempo. El monto queda apartado mientras se confirma la operación.";

    private static final DateTimeFormatter HORA_LOCAL = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final String ALFABETO_UPC = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    /** 10 caracteres: corto para buscarlo facil en la bitacora y en el portal de PuntoRed (acepta de 3 a 15). */
    private static final int LONGITUD_UPC = 10;
    private static final int TIPO_FRONT_RECARGA = 1;
    private static final int TIPO_FRONT_SERVICIO = 2;
    /** Productos con verifyReference: se pagan igual que un servicio, con la referencia ya verificada. */
    private static final int TIPO_FRONT_SERVICIO_VERIFICABLE = 4;

    private final CuentaRepository cuentaRepository;
    private final TransaccionRepository transaccionRepository;
    private final ProductoService productoService;
    private final ValidadorA2F validadorA2F;
    private final RegistroTransacciones registro;
    private final GestoPagoTxGateway gateway;
    private final ObjectMapper objectMapper;
    private final VerificacionReferenciaRepository verificacionRepository;
    private final Clock clock;
    /** Reloj del servidor (sin zona del comercio): con el que se guardan las fechas de las verificaciones. */
    private final Clock relojServidor;
    private final String unidad;
    private final SecureRandom random = new SecureRandom();

    public SendTxHandler(CuentaRepository cuentaRepository,
                         TransaccionRepository transaccionRepository,
                         ProductoService productoService,
                         ValidadorA2F validadorA2F,
                         RegistroTransacciones registro,
                         GestoPagoTxGateway gateway,
                         ObjectMapper objectMapper,
                         VerificacionReferenciaRepository verificacionRepository,
                         Clock clock,
                         @Value("${gestopago.tx.unidad}") String unidad,
                         @Value("${gestopago.tx.zona-horaria}") String zonaHoraria) {
        if (unidad == null || unidad.length() < 3 || unidad.length() > 25) {
            throw new IllegalStateException("gestopago.tx.unidad debe tener de 3 a 25 caracteres");
        }
        this.cuentaRepository = cuentaRepository;
        this.transaccionRepository = transaccionRepository;
        this.productoService = productoService;
        this.validadorA2F = validadorA2F;
        this.registro = registro;
        this.gateway = gateway;
        this.objectMapper = objectMapper;
        this.verificacionRepository = verificacionRepository;
        // horaLocal se genera en la zona horaria del comercio, no en la del servidor
        this.clock = clock.withZone(ZoneId.of(zonaHoraria));
        this.relojServidor = clock;
        this.unidad = unidad;
    }

    @Override
    public ApiResponse<TransaccionResponse> handle(SendTxComando comando) {
        TransaccionRequest request = comando.request();

        // ── 1. Cuenta, propietario, cliente activo y saldo ──
        Optional<Cuenta> encontrada = cuentaRepository.findByNumeroCuenta(request.getNumeroCuenta());
        if (encontrada.isEmpty()) {
            return ApiResponse.error(HttpStatus.NOT_FOUND, MSG_CUENTA_NO_EXISTE);
        }
        Cuenta cuenta = encontrada.get();
        Cliente cliente = cuenta.getCliente();
        ApiResponse<TransaccionResponse> rechazoCuenta = validarCuenta(cuenta, comando.clienteId(), request);
        if (rechazoCuenta != null) {
            return rechazoCuenta;
        }

        // ── 2. Producto del catalogo y reglas de tipoFront (antes del 2FA para no gastar el codigo) ──
        Optional<ProductoDto> producto;
        try {
            producto = productoService.obtenerProductos().stream()
                    .filter(p -> request.getIdServicio().equals(p.getIdServicio()) && request.getIdProducto().equals(p.getIdProducto()))
                    .findFirst();
        } catch (RuntimeException ex) {
            log.error("Catalogo de GestoPago no disponible: {}", ex.getMessage());
            return ApiResponse.error(HttpStatus.SERVICE_UNAVAILABLE, "El catálogo de productos de GestoPago no está disponible. Intente más tarde.");
        }
        if (producto.isEmpty()) {
            return ApiResponse.error(HttpStatus.NOT_FOUND, MSG_PRODUCTO);
        }
        Transaccion nueva = new Transaccion();
        ApiResponse<TransaccionResponse> rechazoPayload = armarDatos(request, producto.get(), nueva);
        if (rechazoPayload != null) {
            return rechazoPayload;
        }
        boolean esServicio = esPagoDeServicio(producto.get());
        if (esServicio) {
            ApiResponse<TransaccionResponse> rechazoVerificacion = validarVerificacion(comando.clienteId(), request);
            if (rechazoVerificacion != null) {
                return rechazoVerificacion;
            }
        }

        // ── 3. Segundo factor ──
        ApiResponse<TransaccionResponse> rechazoA2F = validadorA2F.validar(cliente, request.getCodigoA2F());
        if (rechazoA2F != null) {
            return rechazoA2F;
        }
        // La verificacion autoriza un solo pago: se marca como usada antes de apartar el saldo
        if (esServicio && verificacionRepository.marcarUsada(request.getFolioVerificacion()) == 0) {
            return ApiResponse.error(HttpStatus.CONFLICT, MSG_VERIFICACION_USADA);
        }

        // ── 4. Payload SendTx y saldo apartado ──
        nueva.setUpc(generarUpc());
        nueva.setHoraLocal(HORA_LOCAL.format(LocalDateTime.now(clock)));
        nueva.setUnidad(unidad);
        nueva.setPayloadEnviado(payloadJson(nueva, producto.get()));
        Optional<Transaccion> apartada = registro.apartarSaldo(cuenta.getId(), nueva);
        if (apartada.isEmpty()) {
            // Otro pago simultaneo consumio el saldo entre la validacion y el bloqueo de la cuenta
            return ApiResponse.error(HttpStatus.CONFLICT, MSG_SALDO);
        }
        Transaccion tx = apartada.get();

        // ── 5 y 6. Cifrar, obtener token y enviar ──
        Resultado resultado = gateway.enviar(tx.getPayloadEnviado());

        // ── 7. Respuesta ──
        return responder(tx, resultado, producto.get());
    }

    /** Propietario, cliente y cuenta activos y saldo suficiente. Devuelve null si puede pagar. */
    private ApiResponse<TransaccionResponse> validarCuenta(Cuenta cuenta, Integer clienteId, TransaccionRequest request) {
        Cliente cliente = cuenta.getCliente();
        if (!cliente.getId().equals(clienteId)) {
            log.warn("Cliente ID={} intento pagar con la cuenta del cliente ID={}", clienteId, cliente.getId());
            return ApiResponse.error(HttpStatus.FORBIDDEN, MSG_CUENTA_AJENA);
        }
        if (!Boolean.TRUE.equals(cliente.getActivo()) || cuenta.getEstatus() != EstatusCuenta.ACTIVA) {
            return ApiResponse.error(HttpStatus.FORBIDDEN, MSG_INACTIVO);
        }
        if (cuenta.getSaldo().compareTo(request.getMonto()) < 0) {
            return ApiResponse.error(HttpStatus.CONFLICT, MSG_SALDO);
        }
        return null;
    }

    private ApiResponse<TransaccionResponse> responder(Transaccion tx, Resultado resultado, ProductoDto producto) {
        if (!resultado.enviada()) {
            registro.rechazar(tx.getId(), resultado);
            return ApiResponse.error(HttpStatus.SERVICE_UNAVAILABLE,
                    "No fue posible conectar con GestoPago; no se realizó ningún cargo. Intente más tarde.");
        }
        if (!resultado.tieneRespuesta() || CODIGO_TIMEOUT.equals(resultado.codigo())) {
            Transaccion porConfirmar = registro.marcarPorConfirmar(tx.getId(), resultado);
            return ApiResponse.exito(HttpStatus.ACCEPTED, MSG_POR_CONFIRMAR, conProducto(TransaccionMapper.aResponse(porConfirmar), producto));
        }
        if (CODIGO_EXITO.equals(resultado.codigo())) {
            Transaccion aplicada = registro.aplicar(tx.getId(), resultado);
            return ApiResponse.exito(HttpStatus.OK, MSG_PAGO_EXITOSO, conProducto(TransaccionMapper.aResponse(aplicada), producto));
        }
        Transaccion rechazada = registro.rechazar(tx.getId(), resultado);
        // El texto original de GestoPago queda en la bitacora; al cliente se le responde con un mensaje propio
        RechazoGestoPago rechazo = RechazoGestoPago.de(rechazada.getCodigoRespuesta());
        log.info("Transaccion ID={} rechazada por GestoPago codigo={}", rechazada.getId(), rechazada.getCodigoRespuesta());
        return ApiResponse.error(rechazo.status(), rechazo.mensaje(rechazada.getCodigoRespuesta()));
    }

    /**
     * Reglas de la documentacion de sendTx segun el producto:
     * <ul>
     *   <li>idCatTipoServicio 10 u 11 (canjeables): telefono fijo "#1111111111".</li>
     *   <li>tipoFront 1 (recargas): el telefono o referencia va en "telefono"; el precio es fijo.</li>
     *   <li>tipoFront 2 (pago de servicios): telefono fijo "1111111111", referencia y montoPago obligatorios.</li>
     * </ul>
     */
    private ApiResponse<TransaccionResponse> armarDatos(TransaccionRequest request, ProductoDto producto, Transaccion tx) {
        Integer tipoFront = producto.getTipoFront();
        boolean canjeable = Integer.valueOf(10).equals(producto.getIdCatTipoServicio())
                || Integer.valueOf(11).equals(producto.getIdCatTipoServicio());
        tx.setIdServicio(request.getIdServicio());
        tx.setIdProducto(request.getIdProducto());
        tx.setMonto(request.getMonto());

        if (Integer.valueOf(TIPO_FRONT_RECARGA).equals(tipoFront)) {
            if (producto.getPrecio() != null && BigDecimal.valueOf(producto.getPrecio()).compareTo(request.getMonto()) != 0) {
                return ApiResponse.error(HttpStatus.BAD_REQUEST, "El monto debe ser igual al precio del producto: "
                        + BigDecimal.valueOf(producto.getPrecio()).setScale(2, RoundingMode.HALF_UP) + ".");
            }
            if (canjeable) {
                tx.setTelefono(TELEFONO_CANJEABLES);
            } else if (request.getTelefono() == null) {
                return ApiResponse.error(HttpStatus.BAD_REQUEST, "El teléfono es obligatorio para este producto.");
            } else {
                tx.setTelefono(request.getTelefono());
            }
            return null;
        }
        if (esPagoDeServicio(producto)) {
            if (request.getReferencia() == null) {
                return ApiResponse.error(HttpStatus.BAD_REQUEST, "La referencia es obligatoria para el pago de este servicio.");
            }
            tx.setTelefono(canjeable ? TELEFONO_CANJEABLES : TELEFONO_SERVICIOS);
            tx.setReferencia(request.getReferencia());
            return null;
        }
        return ApiResponse.error(HttpStatus.UNPROCESSABLE_ENTITY,
                "El tipo de producto (tipoFront " + tipoFront + ") no se puede pagar con este servicio.");
    }

    /** Nombre, categoria y logotipo del servicio para mostrar el resultado igual que en el historial. */
    private static TransaccionResponse conProducto(TransaccionResponse res, ProductoDto producto) {
        res.setServicio(producto.getServicio());
        res.setProducto(producto.getProducto());
        res.setCategoria(producto.getCategoria());
        res.setLogoUrl(producto.getLogoUrl());
        return res;
    }

    private static boolean esPagoDeServicio(ProductoDto producto) {
        return Integer.valueOf(TIPO_FRONT_SERVICIO).equals(producto.getTipoFront())
                || Integer.valueOf(TIPO_FRONT_SERVICIO_VERIFICABLE).equals(producto.getTipoFront());
    }

    /**
     * Un pago de servicio solo procede con una verificacion de referencia del mismo cliente, servicio y
     * referencia, que no sea INVALIDA, no haya vencido y no se haya usado.
     */
    private ApiResponse<TransaccionResponse> validarVerificacion(Integer clienteId, TransaccionRequest request) {
        if (request.getFolioVerificacion() == null) {
            return ApiResponse.error(HttpStatus.BAD_REQUEST, MSG_SIN_VERIFICAR);
        }
        Optional<VerificacionReferencia> encontrada = verificacionRepository.findById(request.getFolioVerificacion());
        if (encontrada.isEmpty()) {
            return ApiResponse.error(HttpStatus.CONFLICT, MSG_VERIFICACION_INVALIDA);
        }
        VerificacionReferencia v = encontrada.get();
        boolean corresponde = v.getClienteId().equals(clienteId)
                && v.getIdServicio().equals(request.getIdServicio())
                && v.getReferencia().equals(request.getReferencia())
                && v.getResultado() != ResultadoVerificacion.INVALIDA
                && v.getExpira().isAfter(LocalDateTime.now(relojServidor));
        if (!corresponde) {
            return ApiResponse.error(HttpStatus.CONFLICT, MSG_VERIFICACION_INVALIDA);
        }
        if (Boolean.TRUE.equals(v.getUsada())) {
            return ApiResponse.error(HttpStatus.CONFLICT, MSG_VERIFICACION_USADA);
        }
        return null;
    }

    /** JSON en el orden y con los nombres de la documentacion; solo se incluyen los campos que aplican. */
    private String payloadJson(Transaccion tx, ProductoDto producto) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("idProducto", tx.getIdProducto());
        payload.put("idServicio", tx.getIdServicio());
        payload.put("telefono", tx.getTelefono());
        payload.put("horaLocal", tx.getHoraLocal());
        if (esPagoDeServicio(producto)) {
            payload.put("referencia", tx.getReferencia());
            payload.put("montoPago", tx.getMonto());
        }
        payload.put("upc", tx.getUpc());
        payload.put("unidad", tx.getUnidad());
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No fue posible armar el payload de sendTx", e);
        }
    }

    /** UPC: cadena aleatoria de 10 caracteres alfanumericos, unica por transaccion. */
    private String generarUpc() {
        String upc;
        do {
            StringBuilder sb = new StringBuilder(LONGITUD_UPC);
            for (int i = 0; i < LONGITUD_UPC; i++) {
                sb.append(ALFABETO_UPC.charAt(random.nextInt(ALFABETO_UPC.length())));
            }
            upc = sb.toString();
        } while (transaccionRepository.existsByUpc(upc));
        return upc;
    }
}
