package com.proyecto.servicios.service.impl;

import com.proyecto.servicios.entity.sf.Cuenta;
import com.proyecto.servicios.entity.sf.Deposito;
import com.proyecto.servicios.enums.Catalogos.EstatusCuenta;
import com.proyecto.servicios.repository.sf.CuentaRepository;
import com.proyecto.servicios.repository.sf.DepositoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RegistroDepositos - abono con la cuenta bloqueada")
class RegistroDepositosTest {

    @Mock
    private CuentaRepository cuentaRepository;

    @Mock
    private DepositoRepository depositoRepository;

    private RegistroDepositos registro;
    private Cuenta cuenta;

    @BeforeEach
    void setUp() {
        registro = new RegistroDepositos(cuentaRepository, depositoRepository);
        cuenta = new Cuenta();
        cuenta.setId(10);
        cuenta.setSaldo(new BigDecimal("1000.00"));
        cuenta.setEstatus(EstatusCuenta.ACTIVA);
        when(cuentaRepository.bloquearPorId(10)).thenReturn(Optional.of(cuenta));
        lenient().when(depositoRepository.save(any(Deposito.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("Suma el monto al saldo y registra el saldo resultante en la bitácora")
    void depositar() {
        Optional<Deposito> res = registro.depositar(10, new BigDecimal("250.50"));

        assertThat(res).isPresent();
        assertThat(cuenta.getSaldo()).isEqualByComparingTo("1250.50");
        assertThat(res.get().getSaldoResultante()).isEqualByComparingTo("1250.50");
        assertThat(res.get().getMonto()).isEqualByComparingTo("250.50");
        verify(cuentaRepository).bloquearPorId(10);
    }

    @Test
    @DisplayName("Si la cuenta ya no está activa no abona ni registra")
    void cuentaInactiva() {
        cuenta.setEstatus(EstatusCuenta.INACTIVA);

        assertThat(registro.depositar(10, new BigDecimal("100.00"))).isEmpty();
        assertThat(cuenta.getSaldo()).isEqualByComparingTo("1000.00");
        verify(depositoRepository, never()).save(any());
    }
}
