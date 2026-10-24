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
import com.proyecto.servicios.model.producto.ProductoDto;
import com.proyecto.servicios.repository.sf.CuentaRepository;
import com.proyecto.servicios.repository.sf.TransaccionRepository;
import com.proyecto.servicios.seguridad.IntentosFallidos;
import com.proyecto.servicios.seguridad.Totp;
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
    static final String MSG_A2F_SIN_CONFIGURAR = "Configure su segundo factor antes de realizar pagos.";
    static final String MSG_A2F_INVALIDO = "El código 2FA no es válido o ya fue utilizado.";
    static final String MSG_PAGO_EXITOSO = "Pago exitoso.";
    static final String MSG_POR_CONFIRMAR = "GestoPago no respondió a tiempo. El monto queda apartado mientras se confirma la operación.";

    private static final DateTimeFormatter HORA_LOCAL = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final String ALFABETO_UPC = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    /** 10 caracteres: corto para buscarlo facil en la bitacora y en el portal de PuntoRed (acepta de 3 a 15). */
    private static final int LONGITUD_UPC = 10;
    private static final int TIPO_FRONT_RECARGA = 1;
    private static final int TIPO_FRONT_SERVICIO = 2;

    private final CuentaRepository cuentaRepository;
    private final TransaccionRepository transaccionRepository;
    private final ProductoService productoService;
    private final Totp totp;
    private final IntentosFallidos intentosFallidos;
    private final RegistroTransacciones registro;
    private final GestoPagoTxGateway gateway;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final String unidad;
    private final SecureRandom random = new SecureRandom();

    public SendTxHandler(CuentaRepository cuentaRepository,
                         TransaccionRepository transaccionRepository,
                         ProductoService productoService,
                         Totp totp,
                         IntentosFallidos intentosFallidos,
                         RegistroTransacciones registro,
                         GestoPagoTxGateway gateway,
                         ObjectMapper objectMapper,
                         Clock clock,
                         @Value("${gestopago.tx.unidad}") String unidad,
                         @Value("${gestopago.tx.zona-horaria}") String zonaHoraria) {
        if (unidad == null || unidad.length() < 3 || unidad.length() > 25) {
            throw new IllegalStateException("gestopago.tx.unidad debe tener de 3 a 25 caracteres");
        }
        this.cuentaRepository = cuentaRepository;
        this.transaccionRepository = transaccionRepository;
        this.productoService = productoService;
        this.totp = totp;
        this.intentosFallidos = intentosFallidos;
        this.registro = registro;
        this.gateway = gateway;
        this.objectMapper = objectMapper;
        // horaLocal se genera en la zona horaria del comercio, no en la del servidor
        this.clock = clock.withZone(ZoneId.of(zonaHoraria));
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
        if (!cliente.getId().equals(comando.clienteId())) {
            log.warn("Cliente ID={} intento pagar con la cuenta del cliente ID={}", comando.clienteId(), cliente.getId());
            return ApiResponse.error(HttpStatus.FORBIDDEN, MSG_CUENTA_AJENA);
        }
        if (!Boolean.TRUE.equals(cliente.getActivo()) || cuenta.getEstatus() != EstatusCuenta.ACTIVA) {
            return ApiResponse.error(HttpStatus.FORBIDDEN, MSG_INACTIVO);
        }
        if (cuenta.getSaldo().compareTo(request.getMonto()) < 0) {
            return ApiResponse.error(HttpStatus.CONFLICT, MSG_SALDO);
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

        // ── 3. Segundo factor ──
        ApiResponse<TransaccionResponse> rechazoA2F = validarA2F(cliente, request.getCodigoA2F());
        if (rechazoA2F != null) {
            return rechazoA2F;
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
        return responder(tx, resultado);
    }

    private ApiResponse<TransaccionResponse> responder(Transaccion tx, Resultado resultado) {
        if (!resultado.enviada()) {
            registro.rechazar(tx.getId(), resultado);
            return ApiResponse.error(HttpStatus.SERVICE_UNAVAILABLE,
                    "No fue posible conectar con GestoPago; no se realizó ningún cargo. Intente más tarde.");
        }
        if (!resultado.tieneRespuesta() || CODIGO_TIMEOUT.equals(resultado.codigo())) {
            Transaccion porConfirmar = registro.marcarPorConfirmar(tx.getId(), resultado);
            return ApiResponse.exito(HttpStatus.ACCEPTED, MSG_POR_CONFIRMAR, aResponse(porConfirmar));
        }
        if (CODIGO_EXITO.equals(resultado.codigo())) {
            Transaccion aplicada = registro.aplicar(tx.getId(), resultado);
            return ApiResponse.exito(HttpStatus.OK, MSG_PAGO_EXITOSO, aResponse(aplicada));
        }
        Transaccion rechazada = registro.rechazar(tx.getId(), resultado);
        return ApiResponse.error(HttpStatus.UNPROCESSABLE_ENTITY, "GestoPago rechazó la operación (código "
                + rechazada.getCodigoRespuesta() + "): " + rechazada.getTextoRespuesta() + " No se realizó ningún cargo.");
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
        if (Integer.valueOf(TIPO_FRONT_SERVICIO).equals(tipoFront)) {
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

    /** Valida el codigo TOTP; un codigo incorrecto cuenta para el mismo bloqueo que el inicio de sesion. */
    private ApiResponse<TransaccionResponse> validarA2F(Cliente cliente, String codigo) {
        if (intentosFallidos.estaBloqueado(cliente)) {
            return intentosFallidos.respuestaBloqueo(cliente);
        }
        if (!Boolean.TRUE.equals(cliente.getA2fActivo()) || cliente.getA2fSecreto() == null) {
            return ApiResponse.error(HttpStatus.CONFLICT, MSG_A2F_SIN_CONFIGURAR);
        }
        long paso = totp.verificar(cliente.getA2fSecreto(), codigo, cliente.getA2fUltimoPaso());
        if (paso < 0) {
            return intentosFallidos.registrarFallo(cliente, MSG_A2F_INVALIDO);
        }
        // Guarda el paso usado para que el mismo codigo no autorice otro pago
        cliente.setA2fUltimoPaso(paso);
        intentosFallidos.reiniciar(cliente);
        return null;
    }

    /** JSON en el orden y con los nombres de la documentacion; solo se incluyen los campos que aplican. */
    private String payloadJson(Transaccion tx, ProductoDto producto) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("idProducto", tx.getIdProducto());
        payload.put("idServicio", tx.getIdServicio());
        payload.put("telefono", tx.getTelefono());
        payload.put("horaLocal", tx.getHoraLocal());
        if (Integer.valueOf(TIPO_FRONT_SERVICIO).equals(producto.getTipoFront())) {
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

    private static TransaccionResponse aResponse(Transaccion tx) {
        TransaccionResponse res = new TransaccionResponse();
        res.setId(tx.getId());
        res.setUpc(tx.getUpc());
        res.setEstatus(tx.getEstatus());
        res.setNumeroCuenta(tx.getCuenta().getNumeroCuenta());
        res.setSaldoCuenta(tx.getCuenta().getSaldo());
        res.setIdServicio(tx.getIdServicio());
        res.setIdProducto(tx.getIdProducto());
        res.setTelefono(tx.getTelefono());
        res.setReferencia(tx.getReferencia());
        res.setMonto(tx.getMonto());
        res.setNumAutorizacion(tx.getNumAutorizacion());
        res.setIdTx(tx.getIdTx());
        res.setPin(tx.getPin());
        res.setCodigoGestoPago(tx.getCodigoRespuesta());
        res.setMensajeGestoPago(tx.getTextoRespuesta());
        res.setFecha(tx.getFechaCreacion());
        return res;
    }
}
