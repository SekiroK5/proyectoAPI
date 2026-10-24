package com.proyecto.servicios.service.impl;

import com.proyecto.servicios.client.GestoPagoTxGateway.Resultado;
import com.proyecto.servicios.entity.sf.Cuenta;
import com.proyecto.servicios.entity.sf.Transaccion;
import com.proyecto.servicios.enums.Catalogos.EstatusTransaccion;
import com.proyecto.servicios.model.gestopago.SendTxResponseXml;
import com.proyecto.servicios.repository.sf.CuentaRepository;
import com.proyecto.servicios.repository.sf.TransaccionRepository;
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

@ExtendWith(MockitoExtension.class)
@DisplayName("RegistroTransacciones - saldo apartado, aplicado y devuelto")
class RegistroTransaccionesTest {

    @Mock
    private CuentaRepository cuentaRepository;

    @Mock
    private TransaccionRepository transaccionRepository;

    private RegistroTransacciones registro;
    private Cuenta cuenta;
    private Transaccion tx;

    @BeforeEach
    void setUp() {
        registro = new RegistroTransacciones(cuentaRepository, transaccionRepository);
        cuenta = new Cuenta();
        cuenta.setId(10);
        cuenta.setSaldo(new BigDecimal("100.00"));
        tx = new Transaccion();
        tx.setId(99);
        tx.setMonto(new BigDecimal("30.00"));
        tx.setCuenta(cuenta);
        tx.setEstatus(EstatusTransaccion.PENDIENTE);

        lenient().when(cuentaRepository.bloquearPorId(10)).thenReturn(Optional.of(cuenta));
        lenient().when(transaccionRepository.bloquearPorId(99)).thenReturn(Optional.of(tx));
        lenient().when(transaccionRepository.save(any(Transaccion.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static Resultado respuesta(String codigo) {
        SendTxResponseXml xml = new SendTxResponseXml();
        SendTxResponseXml.Mensaje mensaje = new SendTxResponseXml.Mensaje();
        mensaje.setCodigo(codigo);
        mensaje.setTexto("texto");
        mensaje.setPin("40919958");
        xml.setMensaje(mensaje);
        xml.setIdTx("291437188");
        xml.setNumAutorizacion("100024988");
        return new Resultado(xml, "<RESPONSE>...</RESPONSE>", null, true);
    }

    @Test
    @DisplayName("Aparta el saldo con la cuenta bloqueada y registra la transacción PENDIENTE")
    void apartar() {
        Transaccion nueva = new Transaccion();
        nueva.setMonto(new BigDecimal("30.00"));

        Optional<Transaccion> res = registro.apartarSaldo(10, nueva);

        assertThat(res).isPresent();
        assertThat(cuenta.getSaldo()).isEqualByComparingTo("70.00");
        assertThat(res.get().getEstatus()).isEqualTo(EstatusTransaccion.PENDIENTE);
    }

    @Test
    @DisplayName("Si el saldo ya no alcanza no descuenta ni registra nada")
    void apartar_saldoInsuficiente() {
        Transaccion nueva = new Transaccion();
        nueva.setMonto(new BigDecimal("100.01"));

        assertThat(registro.apartarSaldo(10, nueva)).isEmpty();
        assertThat(cuenta.getSaldo()).isEqualByComparingTo("100.00");
        verify(transaccionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Aplicar guarda la respuesta completa (bitácora) sin mover el saldo")
    void aplicar() {
        Transaccion res = registro.aplicar(99, respuesta("01"));

        assertThat(res.getEstatus()).isEqualTo(EstatusTransaccion.APLICADA);
        assertThat(res.getNumAutorizacion()).isEqualTo("100024988");
        assertThat(res.getIdTx()).isEqualTo("291437188");
        assertThat(res.getPin()).isEqualTo("40919958");
        assertThat(res.getRespuesta()).isEqualTo("<RESPONSE>...</RESPONSE>");
        assertThat(cuenta.getSaldo()).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("Rechazar devuelve el saldo una sola vez")
    void rechazar_devuelveUnaVez() {
        registro.rechazar(99, respuesta("02"));
        registro.rechazar(99, respuesta("02"));

        assertThat(tx.getEstatus()).isEqualTo(EstatusTransaccion.RECHAZADA);
        assertThat(cuenta.getSaldo()).isEqualByComparingTo("130.00");
    }

    @Test
    @DisplayName("Nunca devuelve el saldo de una transacción ya aplicada")
    void rechazar_aplicadaNoDevuelve() {
        tx.setEstatus(EstatusTransaccion.APLICADA);

        registro.rechazar(99, respuesta("70"));

        assertThat(tx.getEstatus()).isEqualTo(EstatusTransaccion.APLICADA);
        assertThat(cuenta.getSaldo()).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("Por confirmar: conserva el saldo apartado y guarda el motivo")
    void porConfirmar() {
        Transaccion res = registro.marcarPorConfirmar(99, new Resultado(null, null, "Read timed out", true));

        assertThat(res.getEstatus()).isEqualTo(EstatusTransaccion.PENDIENTE_CONFIRMACION);
        assertThat(res.getTextoRespuesta()).isEqualTo("Read timed out");
        assertThat(cuenta.getSaldo()).isEqualByComparingTo("100.00");
    }
}
