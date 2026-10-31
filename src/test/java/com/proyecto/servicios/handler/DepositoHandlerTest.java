package com.proyecto.servicios.handler;

import com.proyecto.servicios.dto.request.DepositoRequest;
import com.proyecto.servicios.dto.response.ApiResponse;
import com.proyecto.servicios.dto.response.DepositoResponse;
import com.proyecto.servicios.entity.sf.Cliente;
import com.proyecto.servicios.entity.sf.Cuenta;
import com.proyecto.servicios.entity.sf.Deposito;
import com.proyecto.servicios.enums.Catalogos.EstatusCuenta;
import com.proyecto.servicios.enums.Catalogos.MetodoA2F;
import com.proyecto.servicios.repository.sf.ClienteRepository;
import com.proyecto.servicios.repository.sf.CuentaRepository;
import com.proyecto.servicios.seguridad.IntentosFallidos;
import com.proyecto.servicios.seguridad.Totp;
import com.proyecto.servicios.seguridad.ValidadorA2F;
import com.proyecto.servicios.service.impl.RegistroDepositos;
import org.apache.commons.codec.binary.Base32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DepositoHandler - depósito a la cuenta propia")
class DepositoHandlerTest {

    private static final String NUMERO_CUENTA = "4152313748382907";
    private static final Integer CLIENTE_ID = 1;
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-10-30T18:00:00Z"), ZoneOffset.UTC);

    @Mock private CuentaRepository cuentaRepository;
    @Mock private ClienteRepository clienteRepository;
    @Mock private RegistroDepositos registro;

    private final Totp totp = new Totp(RELOJ);
    private DepositoHandler handler;
    private Cliente cliente;
    private Cuenta cuenta;

    @BeforeEach
    void setUp() {
        handler = new DepositoHandler(cuentaRepository,
                new ValidadorA2F(totp, new IntentosFallidos(clienteRepository, RELOJ, 5, 15), clienteRepository),
                registro, new BigDecimal("50000.00"));

        cliente = new Cliente();
        cliente.setId(CLIENTE_ID);
        cliente.setActivo(true);
        cliente.setA2fMetodo(MetodoA2F.TOTP);
        cliente.setA2fSecreto(totp.generarSecreto());
        cliente.setA2fActivo(true);
        cuenta = new Cuenta();
        cuenta.setId(10);
        cuenta.setNumeroCuenta(NUMERO_CUENTA);
        cuenta.setSaldo(new BigDecimal("1000.00"));
        cuenta.setEstatus(EstatusCuenta.ACTIVA);
        cuenta.setCliente(cliente);

        lenient().when(cuentaRepository.findByNumeroCuenta(NUMERO_CUENTA)).thenReturn(Optional.of(cuenta));
        lenient().when(clienteRepository.marcarPasoA2F(eq(CLIENTE_ID), anyLong())).thenReturn(1);
        lenient().when(registro.depositar(eq(10), any(BigDecimal.class))).thenAnswer(inv -> {
            BigDecimal monto = inv.getArgument(1);
            Deposito d = new Deposito();
            d.setId(7);
            d.setCuenta(cuenta);
            d.setMonto(monto);
            d.setSaldoResultante(cuenta.getSaldo().add(monto));
            return Optional.of(d);
        });
    }

    private String codigoActual() {
        return Totp.generarCodigo(new Base32().decode(cliente.getA2fSecreto()), RELOJ.millis() / 1000 / 30);
    }

    private DepositoRequest deposito(String monto) {
        DepositoRequest req = new DepositoRequest();
        req.setNumeroCuenta(NUMERO_CUENTA);
        req.setMonto(new BigDecimal(monto));
        req.setCodigoA2F(codigoActual());
        return req;
    }

    private ApiResponse<DepositoResponse> ejecutar(DepositoRequest req) {
        return handler.handle(new DepositoComando(CLIENTE_ID, req));
    }

    @Test
    @DisplayName("Depósito correcto: 200 con folio y saldo resultante")
    void exito() {
        ApiResponse<DepositoResponse> res = ejecutar(deposito("500.00"));

        assertThat(res.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(res.getMensaje()).isEqualTo(DepositoHandler.MSG_EXITO);
        assertThat(res.getDatos().getFolio()).isEqualTo(7);
        assertThat(res.getDatos().getSaldo()).isEqualByComparingTo("1500.00");
        verify(registro).depositar(10, new BigDecimal("500.00"));
    }

    @Test
    @DisplayName("Cuenta inexistente: 404")
    void cuentaInexistente() {
        DepositoRequest req = deposito("100.00");
        req.setNumeroCuenta("0000000000000000");

        assertThat(ejecutar(req).getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("No se puede depositar a la cuenta de otro cliente: 403")
    void cuentaAjena() {
        ApiResponse<DepositoResponse> res = handler.handle(new DepositoComando(2, deposito("100.00")));

        assertThat(res.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(res.getMensaje()).isEqualTo(DepositoHandler.MSG_CUENTA_AJENA);
        verify(registro, never()).depositar(any(), any());
    }

    @Test
    @DisplayName("Cuenta inactiva: 403")
    void cuentaInactiva() {
        cuenta.setEstatus(EstatusCuenta.INACTIVA);

        assertThat(ejecutar(deposito("100.00")).getMensaje()).isEqualTo(DepositoHandler.MSG_INACTIVO);
    }

    @Test
    @DisplayName("Monto mayor al máximo por depósito: 400 antes de pedir el 2FA")
    void montoMayorAlMaximo() {
        ApiResponse<DepositoResponse> res = ejecutar(deposito("50000.01"));

        assertThat(res.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(res.getMensaje()).contains("50000.00");
        verify(clienteRepository, never()).marcarPasoA2F(any(), anyLong());
    }

    @Test
    @DisplayName("Código 2FA incorrecto: 401 y no abona")
    void a2fIncorrecto() {
        DepositoRequest req = deposito("100.00");
        req.setCodigoA2F(codigoActual().equals("000000") ? "111111" : "000000");

        assertThat(ejecutar(req).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(registro, never()).depositar(any(), any());
    }

    @Test
    @DisplayName("Doble clic: si otra petición ya usó el código, 409 y no abona dos veces")
    void dobleClic() {
        when(clienteRepository.marcarPasoA2F(eq(CLIENTE_ID), anyLong())).thenReturn(0);

        ApiResponse<DepositoResponse> res = ejecutar(deposito("100.00"));

        assertThat(res.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        verify(registro, never()).depositar(any(), any());
    }

    @Test
    @DisplayName("Si la cuenta se dio de baja al bloquearla: 403")
    void cuentaDadaDeBajaAlBloquear() {
        when(registro.depositar(eq(10), any(BigDecimal.class))).thenReturn(Optional.empty());

        assertThat(ejecutar(deposito("100.00")).getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
