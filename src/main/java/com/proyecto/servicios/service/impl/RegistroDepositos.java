package com.proyecto.servicios.service.impl;

import com.proyecto.servicios.entity.sf.Cuenta;
import com.proyecto.servicios.entity.sf.Deposito;
import com.proyecto.servicios.enums.Catalogos.EstatusCuenta;
import com.proyecto.servicios.repository.sf.CuentaRepository;
import com.proyecto.servicios.repository.sf.DepositoRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Abona el deposito con la cuenta bloqueada (SELECT ... FOR UPDATE), en la misma transaccion que lo
 * registra en la bitacora. Asi un deposito y un pago simultaneos nunca leen el mismo saldo.
 */
@Service
@Slf4j
public class RegistroDepositos {

    private final CuentaRepository cuentaRepository;
    private final DepositoRepository depositoRepository;

    public RegistroDepositos(CuentaRepository cuentaRepository, DepositoRepository depositoRepository) {
        this.cuentaRepository = cuentaRepository;
        this.depositoRepository = depositoRepository;
    }

    /** @return el deposito registrado, o vacio si la cuenta ya no esta activa al bloquearla */
    @Transactional("sfTransactionManager")
    public Optional<Deposito> depositar(Integer cuentaId, BigDecimal monto) {
        Cuenta cuenta = cuentaRepository.bloquearPorId(cuentaId)
                .orElseThrow(() -> new IllegalStateException("No existe la cuenta " + cuentaId));
        if (cuenta.getEstatus() != EstatusCuenta.ACTIVA) {
            return Optional.empty();
        }
        cuenta.setSaldo(cuenta.getSaldo().add(monto));

        Deposito deposito = new Deposito();
        deposito.setCuenta(cuenta);
        deposito.setMonto(monto);
        deposito.setSaldoResultante(cuenta.getSaldo());
        Deposito guardado = depositoRepository.save(deposito);
        log.info("Deposito ID={} a cuenta ID={} por {}", guardado.getId(), cuentaId, monto);
        return Optional.of(guardado);
    }
}
