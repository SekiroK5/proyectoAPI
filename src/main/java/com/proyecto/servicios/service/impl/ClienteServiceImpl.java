package com.proyecto.servicios.service.impl;

import com.proyecto.servicios.dto.request.ClienteBusquedaRequest;
import com.proyecto.servicios.dto.request.ClienteConsultaRequest;
import com.proyecto.servicios.dto.request.ClienteCreateRequest;
import com.proyecto.servicios.dto.request.ClienteEliminacionRequest;
import com.proyecto.servicios.dto.request.ClienteLlaveRequest;
import com.proyecto.servicios.dto.request.ClienteUpdateRequest;
import com.proyecto.servicios.dto.response.ApiResponse;
import com.proyecto.servicios.dto.response.ClienteResponse;
import com.proyecto.servicios.dto.response.PaginaResponse;
import com.proyecto.servicios.entity.sf.Cliente;
import com.proyecto.servicios.entity.sf.Cuenta;
import com.proyecto.servicios.enums.Catalogos.EstatusCuenta;
import com.proyecto.servicios.mapper.ClienteMapper;
import com.proyecto.servicios.repository.sf.ClienteRepository;
import com.proyecto.servicios.repository.sf.CuentaRepository;
import com.proyecto.servicios.service.ClienteService;
import com.proyecto.servicios.specification.ClienteSpecification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.util.Optional;

@Service
@Slf4j
public class ClienteServiceImpl implements ClienteService {

    static final String MSG_NO_ENCONTRADO = "No existe un cliente con el nombre y correo electrónico proporcionados.";
    static final String MSG_CORREO_DUPLICADO = "El correo ya se encuentra registrado. Intente con otro correo.";
    static final String MSG_CURP_DUPLICADA = "Ya existe un cliente registrado con la CURP proporcionada.";
    static final String MSG_RFC_DUPLICADO = "Ya existe un cliente registrado con el RFC proporcionado.";
    static final String MSG_USUARIO_DUPLICADO = "El nombre de usuario ya se encuentra registrado. Intente con otro.";

    private static final int MAX_INTENTOS_NUMERO_CUENTA = 10;
    private static final Sort ORDEN = Sort.by("id");

    private final ClienteRepository clienteRepository;
    private final CuentaRepository cuentaRepository;
    private final BigDecimal saldoInicial;
    private final SecureRandom random = new SecureRandom();

    public ClienteServiceImpl(ClienteRepository clienteRepository,
                              CuentaRepository cuentaRepository,
                              @Value("${onboarding.cuenta.saldo-inicial:0.00}") BigDecimal saldoInicial) {
        // Regla: el saldo inicial lo define el sistema y no puede ser negativo
        if (saldoInicial == null || saldoInicial.signum() < 0) {
            throw new IllegalStateException("La propiedad onboarding.cuenta.saldo-inicial no puede ser negativa");
        }
        this.clienteRepository = clienteRepository;
        this.cuentaRepository = cuentaRepository;
        this.saldoInicial = saldoInicial.setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    @Transactional("sfTransactionManager")
    public ApiResponse<ClienteResponse> registrar(ClienteCreateRequest request) {
        // La mayoria de edad y los formatos ya los validaron las anotaciones del DTO (@Valid)
        String duplicado = buscarDatoDuplicado(request);
        if (duplicado != null) {
            log.warn("Alta rechazada: {}", duplicado);
            return ApiResponse.error(HttpStatus.CONFLICT, duplicado);
        }

        Cliente cliente = ClienteMapper.aEntidad(request);
        cliente.setActivo(true);
        Cliente guardado = clienteRepository.save(cliente);

        Cuenta cuenta = new Cuenta();
        cuenta.setNumeroCuenta(generarNumeroCuentaUnico());
        cuenta.setCliente(guardado);
        cuenta.setSaldo(saldoInicial);
        cuenta.setEstatus(EstatusCuenta.ACTIVA);
        guardado.setCuenta(cuentaRepository.save(cuenta));

        log.info("Cliente registrado ID={}, cuenta={}", guardado.getId(), cuenta.getNumeroCuenta());
        return ApiResponse.exito(HttpStatus.CREATED, "Cliente registrado correctamente.", ClienteMapper.aResponse(guardado));
    }

    @Override
    @Transactional(value = "sfTransactionManager", readOnly = true)
    public ApiResponse<PaginaResponse<ClienteResponse>> listar() {
        return buscar(new ClienteBusquedaRequest());
    }

    @Override
    @Transactional(value = "sfTransactionManager", readOnly = true)
    public ApiResponse<PaginaResponse<ClienteResponse>> buscar(ClienteBusquedaRequest filtro) {
        PaginaResponse<ClienteResponse> pagina = PaginaResponse.of(
                clienteRepository.findAll(ClienteSpecification.desde(filtro), filtro.toPageable(ORDEN)),
                ClienteMapper::aResponse);
        String mensaje = pagina.getTotalElementos() == 0
                ? "No se encontraron clientes con los criterios proporcionados."
                : "Se encontraron " + pagina.getTotalElementos() + " cliente(s).";
        return ApiResponse.exito(HttpStatus.OK, mensaje, pagina);
    }

    @Override
    @Transactional(value = "sfTransactionManager", readOnly = true)
    public ApiResponse<ClienteResponse> consultar(ClienteConsultaRequest request) {
        return localizar(request)
                .map(c -> ApiResponse.exito(HttpStatus.OK, "Cliente encontrado.", ClienteMapper.aResponse(c)))
                .orElseGet(ClienteServiceImpl::noEncontrado);
    }

    @Override
    @Transactional("sfTransactionManager")
    public ApiResponse<ClienteResponse> actualizar(ClienteUpdateRequest request) {
        Optional<Cliente> encontrado = localizar(request);
        if (encontrado.isEmpty()) {
            return noEncontrado();
        }
        Cliente cliente = encontrado.get();

        boolean reactivar = Boolean.TRUE.equals(request.getActivo());
        if (!Boolean.TRUE.equals(cliente.getActivo()) && !reactivar) {
            return ApiResponse.error(HttpStatus.CONFLICT,
                    "El cliente está dado de baja. Para modificarlo primero reactívelo enviando \"activo\": true.");
        }
        String nuevoCorreo = request.getNuevoCorreoElectronico();
        if (nuevoCorreo != null && clienteRepository.existsByCorreoElectronicoAndIdNot(nuevoCorreo, cliente.getId())) {
            return ApiResponse.error(HttpStatus.CONFLICT, MSG_CORREO_DUPLICADO);
        }

        ClienteMapper.aplicarCambios(request, cliente);
        if (request.getActivo() != null) {
            cambiarEstado(cliente, request.getActivo());
        }
        Cliente actualizado = clienteRepository.saveAndFlush(cliente);

        log.info("Cliente ID={} actualizado", actualizado.getId());
        return ApiResponse.exito(HttpStatus.OK, "Cliente actualizado correctamente.", ClienteMapper.aResponse(actualizado));
    }

    @Override
    @Transactional("sfTransactionManager")
    public ApiResponse<ClienteResponse> eliminar(ClienteEliminacionRequest request) {
        Optional<Cliente> encontrado = localizar(request);
        if (encontrado.isEmpty()) {
            return noEncontrado();
        }
        Cliente cliente = encontrado.get();
        if (!Boolean.TRUE.equals(cliente.getActivo())) {
            return ApiResponse.error(HttpStatus.CONFLICT, "El cliente ya se encuentra dado de baja.");
        }

        cambiarEstado(cliente, false);
        Cliente actualizado = clienteRepository.saveAndFlush(cliente);

        log.info("Cliente ID={} dado de baja (eliminacion logica)", actualizado.getId());
        return ApiResponse.exito(HttpStatus.OK, "Cliente eliminado correctamente (baja lógica).", ClienteMapper.aResponse(actualizado));
    }

    /**
     * Localiza al cliente con la llave compuesta. Acepta cualquier subtipo de ClienteLlaveRequest
     * (consulta, actualizacion o eliminacion).
     */
    private Optional<Cliente> localizar(ClienteLlaveRequest llave) {
        return clienteRepository.findByNombreIgnoreCaseAndCorreoElectronico(llave.getNombre(), llave.getCorreoElectronico());
    }

    private static ApiResponse<ClienteResponse> noEncontrado() {
        return ApiResponse.error(HttpStatus.NOT_FOUND, MSG_NO_ENCONTRADO);
    }

    /** Regla: solo los clientes activos pueden tener cuentas activas. */
    private void cambiarEstado(Cliente cliente, boolean activo) {
        cliente.setActivo(activo);
        if (cliente.getCuenta() != null) {
            cliente.getCuenta().setEstatus(activo ? EstatusCuenta.ACTIVA : EstatusCuenta.INACTIVA);
        }
    }

    /** Devuelve el motivo si CURP, RFC, correo o usuario ya existen; null si todos son unicos. */
    private String buscarDatoDuplicado(ClienteCreateRequest request) {
        if (clienteRepository.existsByCurp(request.getCurp())) {
            return MSG_CURP_DUPLICADA;
        }
        if (clienteRepository.existsByRfc(request.getRfc())) {
            return MSG_RFC_DUPLICADO;
        }
        if (clienteRepository.existsByCorreoElectronico(request.getCorreoElectronico())) {
            return MSG_CORREO_DUPLICADO;
        }
        if (clienteRepository.existsByNombreUsuario(request.getNombreUsuario())) {
            return MSG_USUARIO_DUPLICADO;
        }
        return null;
    }

    /**
     * Genera un numero de cuenta de 10 digitos verificando que no exista.
     * La restriccion UNIQUE en base de datos cubre el caso de concurrencia.
     */
    private String generarNumeroCuentaUnico() {
        for (int intento = 0; intento < MAX_INTENTOS_NUMERO_CUENTA; intento++) {
            String numeroCuenta = String.valueOf(1_000_000_000L + random.nextLong(9_000_000_000L));
            if (!cuentaRepository.existsByNumeroCuenta(numeroCuenta)) {
                return numeroCuenta;
            }
        }
        throw new IllegalStateException("No fue posible generar un numero de cuenta unico");
    }
}
