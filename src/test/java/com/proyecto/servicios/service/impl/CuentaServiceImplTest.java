package com.proyecto.servicios.service.impl;

import com.proyecto.servicios.dto.request.CuentaBusquedaRequest;
import com.proyecto.servicios.dto.request.CuentaConsultaRequest;
import com.proyecto.servicios.dto.response.ApiResponse;
import com.proyecto.servicios.dto.response.CuentaResponse;
import com.proyecto.servicios.entity.sf.Cliente;
import com.proyecto.servicios.entity.sf.Cuenta;
import com.proyecto.servicios.enums.Catalogos.EstatusCuenta;
import com.proyecto.servicios.repository.sf.CuentaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CuentaServiceImpl - consultas de cuentas")
class CuentaServiceImplTest {

    @Mock
    private CuentaRepository cuentaRepository;

    @InjectMocks
    private CuentaServiceImpl cuentaService;

    private static Cuenta cuenta() {
        Cliente cliente = new Cliente();
        cliente.setNombre("Ana");
        cliente.setApellidoPaterno("Ruiz");
        cliente.setApellidoMaterno("Díaz");
        cliente.setCorreoElectronico("ana@correo.com");
        cliente.setActivo(true);
        Cuenta cuenta = new Cuenta();
        cuenta.setNumeroCuenta("1234567890");
        cuenta.setSaldo(new BigDecimal("150.50"));
        cuenta.setEstatus(EstatusCuenta.ACTIVA);
        cuenta.setCliente(cliente);
        return cuenta;
    }

    private static CuentaConsultaRequest consulta(String numero) {
        CuentaConsultaRequest req = new CuentaConsultaRequest();
        req.setNumeroCuenta(numero);
        return req;
    }

    @Test
    @DisplayName("Consulta la cuenta con su saldo y titular")
    void consultar_existente() {
        when(cuentaRepository.findByNumeroCuenta("1234567890")).thenReturn(Optional.of(cuenta()));

        CuentaResponse res = cuentaService.consultar(consulta("1234567890")).getDatos();

        assertThat(res.getSaldo()).isEqualByComparingTo("150.50");
        assertThat(res.getNombreCliente()).isEqualTo("Ana Ruiz Díaz");
        assertThat(res.getCorreoCliente()).isEqualTo("ana@correo.com");
    }

    @Test
    @DisplayName("Cuenta inexistente -> codigo 1, 404")
    void consultar_inexistente() {
        when(cuentaRepository.findByNumeroCuenta("0000000000")).thenReturn(Optional.empty());

        ApiResponse<CuentaResponse> res = cuentaService.consultar(consulta("0000000000"));

        assertThat(res.getCodigo()).isEqualTo(ApiResponse.ERROR);
        assertThat(res.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(res.getMensaje()).contains("0000000000");
    }

    @Test
    @DisplayName("Búsqueda paginada de cuentas activas")
    @SuppressWarnings("unchecked")
    void buscar_activas() {
        when(cuentaRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(cuenta())));
        CuentaBusquedaRequest filtro = new CuentaBusquedaRequest();
        filtro.setEstatus(EstatusCuenta.ACTIVA);

        assertThat(cuentaService.buscar(filtro).getDatos().getContenido()).hasSize(1);
    }
}
