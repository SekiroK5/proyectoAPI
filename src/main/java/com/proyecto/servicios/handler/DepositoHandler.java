package com.proyecto.servicios.handler;

import com.proyecto.servicios.dto.request.DepositoRequest;
import com.proyecto.servicios.dto.response.ApiResponse;
import com.proyecto.servicios.dto.response.DepositoResponse;
import com.proyecto.servicios.entity.sf.Cliente;
import com.proyecto.servicios.entity.sf.Cuenta;
import com.proyecto.servicios.entity.sf.Deposito;
import com.proyecto.servicios.enums.Catalogos.EstatusCuenta;
import com.proyecto.servicios.repository.sf.CuentaRepository;
import com.proyecto.servicios.seguridad.ValidadorA2F;
import com.proyecto.servicios.service.impl.RegistroDepositos;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * Deposito a la cuenta propia:
 * <ol>
 *   <li>La cuenta existe y es del usuario de la sesion; cliente y cuenta activos.</li>
 *   <li>El monto no rebasa el maximo por deposito que define el sistema.</li>
 *   <li>El codigo 2FA autoriza la operacion (un codigo, un deposito).</li>
 *   <li>Se abona con la cuenta bloqueada y se registra en la bitacora de depositos.</li>
 * </ol>
 */
@Component
@Slf4j
public class DepositoHandler implements Handler<DepositoComando, DepositoResponse> {

    static final String MSG_CUENTA_NO_EXISTE = "No existe una cuenta con el número proporcionado.";
    static final String MSG_CUENTA_AJENA = "Solo puede depositar a una cuenta propia.";
    static final String MSG_INACTIVO = "El cliente o la cuenta no están activos.";
    static final String MSG_EXITO = "Depósito realizado correctamente.";

    private final CuentaRepository cuentaRepository;
    private final ValidadorA2F validadorA2F;
    private final RegistroDepositos registro;
    private final BigDecimal montoMaximo;

    public DepositoHandler(CuentaRepository cuentaRepository,
                           ValidadorA2F validadorA2F,
                           RegistroDepositos registro,
                           @Value("${onboarding.deposito.monto-maximo:50000.00}") BigDecimal montoMaximo) {
        if (montoMaximo == null || montoMaximo.signum() <= 0) {
            throw new IllegalStateException("onboarding.deposito.monto-maximo debe ser mayor a cero");
        }
        this.cuentaRepository = cuentaRepository;
        this.validadorA2F = validadorA2F;
        this.registro = registro;
        this.montoMaximo = montoMaximo;
    }

    @Override
    public ApiResponse<DepositoResponse> handle(DepositoComando comando) {
        DepositoRequest request = comando.request();

        Optional<Cuenta> encontrada = cuentaRepository.findByNumeroCuenta(request.getNumeroCuenta());
        if (encontrada.isEmpty()) {
            return ApiResponse.error(HttpStatus.NOT_FOUND, MSG_CUENTA_NO_EXISTE);
        }
        Cuenta cuenta = encontrada.get();
        Cliente cliente = cuenta.getCliente();
        if (!cliente.getId().equals(comando.clienteId())) {
            log.warn("Cliente ID={} intento depositar a la cuenta del cliente ID={}", comando.clienteId(), cliente.getId());
            return ApiResponse.error(HttpStatus.FORBIDDEN, MSG_CUENTA_AJENA);
        }
        if (!Boolean.TRUE.equals(cliente.getActivo()) || cuenta.getEstatus() != EstatusCuenta.ACTIVA) {
            return ApiResponse.error(HttpStatus.FORBIDDEN, MSG_INACTIVO);
        }
        if (request.getMonto().compareTo(montoMaximo) > 0) {
            return ApiResponse.error(HttpStatus.BAD_REQUEST, "El monto máximo por depósito es "
                    + montoMaximo.setScale(2, RoundingMode.HALF_UP) + ".");
        }

        ApiResponse<DepositoResponse> rechazoA2F = validadorA2F.validar(cliente, request.getCodigoA2F());
        if (rechazoA2F != null) {
            return rechazoA2F;
        }

        Optional<Deposito> deposito = registro.depositar(cuenta.getId(), request.getMonto());
        if (deposito.isEmpty()) {
            // La cuenta se dio de baja entre la validacion y el bloqueo
            return ApiResponse.error(HttpStatus.FORBIDDEN, MSG_INACTIVO);
        }
        return ApiResponse.exito(HttpStatus.OK, MSG_EXITO, aResponse(deposito.get(), cuenta.getNumeroCuenta()));
    }

    private static DepositoResponse aResponse(Deposito deposito, String numeroCuenta) {
        DepositoResponse res = new DepositoResponse();
        res.setFolio(deposito.getId());
        res.setNumeroCuenta(numeroCuenta);
        res.setMonto(deposito.getMonto());
        res.setSaldo(deposito.getSaldoResultante());
        res.setFecha(deposito.getFechaCreacion());
        return res;
    }
}
