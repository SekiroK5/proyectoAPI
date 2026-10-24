package com.proyecto.servicios.service.impl;

import com.proyecto.servicios.dto.request.ClienteBusquedaRequest;
import com.proyecto.servicios.dto.request.ClienteConsultaRequest;
import com.proyecto.servicios.dto.request.ClienteCreateRequest;
import com.proyecto.servicios.dto.request.ClienteEliminacionRequest;
import com.proyecto.servicios.dto.request.ClienteUpdateRequest;
import com.proyecto.servicios.dto.request.DomicilioRequest;
import com.proyecto.servicios.dto.request.DomicilioUpdateRequest;
import com.proyecto.servicios.dto.response.ApiResponse;
import com.proyecto.servicios.dto.response.ClienteResponse;
import com.proyecto.servicios.dto.response.PaginaResponse;
import com.proyecto.servicios.entity.sf.Cliente;
import com.proyecto.servicios.entity.sf.Cuenta;
import com.proyecto.servicios.enums.Catalogos.EstadoCivil;
import com.proyecto.servicios.enums.Catalogos.EstatusCuenta;
import com.proyecto.servicios.enums.Catalogos.Sexo;
import com.proyecto.servicios.mapper.ClienteMapper;
import com.proyecto.servicios.repository.sf.ClienteRepository;
import com.proyecto.servicios.repository.sf.CuentaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ClienteServiceImpl - reglas de negocio")
class ClienteServiceImplTest {

    private static final String NOMBRE = "Juan";
    private static final String CORREO = "juan.perez@correo.com";

    @Mock
    private ClienteRepository clienteRepository;

    @Mock
    private CuentaRepository cuentaRepository;

    private ClienteServiceImpl clienteService;

    @BeforeEach
    void setUp() {
        clienteService = new ClienteServiceImpl(clienteRepository, cuentaRepository, new BigDecimal("0.00"));
    }

    // ======================== Datos de prueba ========================

    private static ClienteCreateRequest altaValida() {
        DomicilioRequest dom = new DomicilioRequest();
        dom.setCalle("Av. Reforma");
        dom.setNumeroExterior("123");
        dom.setColonia("Centro");
        dom.setMunicipio("Cuauhtémoc");
        dom.setEstado("CDMX");
        dom.setCodigoPostal("06000");
        dom.setPais("México");

        ClienteCreateRequest req = new ClienteCreateRequest();
        req.setNombre(NOMBRE);
        req.setApellidoPaterno("Pérez");
        req.setApellidoMaterno("López");
        req.setFechaNacimiento(LocalDate.of(1995, 5, 20));
        req.setCurp("PELJ950520HDFRRN01");
        req.setRfc("PELJ950520AB1");
        req.setSexo(Sexo.M);
        req.setNacionalidad("Mexicana");
        req.setEstadoCivil(EstadoCivil.SOLTERO);
        req.setCorreoElectronico(CORREO);
        req.setNombreUsuario("juanperez95");
        req.setTelefonoMovil("4771234567");
        req.setOcupacion("Desarrollador");
        req.setEmpresa("Tech Corp");
        req.setIngresoMensual(new BigDecimal("25000.00"));
        req.setDomicilio(dom);
        return req;
    }

    private static Cliente clienteExistente(boolean activo) {
        Cliente cliente = ClienteMapper.aEntidad(altaValida());
        cliente.setId(1);
        cliente.setActivo(activo);
        cliente.setTelefonoAlternativo("4770000000");
        Cuenta cuenta = new Cuenta();
        cuenta.setNumeroCuenta("1234567890");
        cuenta.setSaldo(new BigDecimal("0.00"));
        cuenta.setEstatus(activo ? EstatusCuenta.ACTIVA : EstatusCuenta.INACTIVA);
        cuenta.setCliente(cliente);
        cliente.setCuenta(cuenta);
        return cliente;
    }

    private static ClienteUpdateRequest actualizacion() {
        ClienteUpdateRequest req = new ClienteUpdateRequest();
        req.setNombre(NOMBRE);
        req.setCorreoElectronico(CORREO);
        return req;
    }

    private static ClienteEliminacionRequest eliminacion() {
        ClienteEliminacionRequest req = new ClienteEliminacionRequest();
        req.setNombre(NOMBRE);
        req.setCorreoElectronico(CORREO);
        return req;
    }

    private void existe(Cliente cliente) {
        when(clienteRepository.findByNombreIgnoreCaseAndCorreoElectronico(NOMBRE, CORREO)).thenReturn(Optional.of(cliente));
    }

    private void guardarDevuelveArgumento() {
        when(clienteRepository.saveAndFlush(any(Cliente.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static void esError(ApiResponse<?> res, HttpStatus status, String mensaje) {
        assertThat(res.getCodigo()).isEqualTo(ApiResponse.ERROR);
        assertThat(res.getStatus()).isEqualTo(status);
        assertThat(res.getMensaje()).isEqualTo(mensaje);
        assertThat(res.getDatos()).isNull();
    }

    // ======================== Alta ========================

    @Test
    @DisplayName("Registra al cliente y abre una cuenta ACTIVA con el saldo inicial del sistema")
    void registrar_exitoso() {
        when(clienteRepository.save(any(Cliente.class))).thenAnswer(inv -> {
            Cliente c = inv.getArgument(0);
            c.setId(1);
            return c;
        });
        when(cuentaRepository.save(any(Cuenta.class))).thenAnswer(inv -> inv.getArgument(0));

        ApiResponse<ClienteResponse> res = clienteService.registrar(altaValida());

        assertThat(res.getCodigo()).isEqualTo(ApiResponse.EXITO);
        assertThat(res.getStatus()).isEqualTo(HttpStatus.CREATED);
        assertThat(res.getDatos().getId()).isEqualTo(1);
        assertThat(res.getDatos().getActivo()).isTrue();
        assertThat(res.getDatos().getNumeroCuenta()).hasSize(10).containsOnlyDigits();
        assertThat(res.getDatos().getSaldoCuenta()).isEqualByComparingTo("0.00");
        assertThat(res.getDatos().getEstatusCuenta()).isEqualTo(EstatusCuenta.ACTIVA);
    }

    @Test
    @DisplayName("Reintenta el número de cuenta si el generado ya existe")
    void registrar_numeroCuentaRepetido_reintenta() {
        when(cuentaRepository.existsByNumeroCuenta(anyString())).thenReturn(true, false);
        when(clienteRepository.save(any(Cliente.class))).thenAnswer(inv -> inv.getArgument(0));
        when(cuentaRepository.save(any(Cuenta.class))).thenAnswer(inv -> inv.getArgument(0));

        clienteService.registrar(altaValida());

        verify(cuentaRepository, times(2)).existsByNumeroCuenta(anyString());
    }

    @Test
    @DisplayName("No arranca si el saldo inicial configurado es negativo")
    void saldoInicialNegativo() {
        assertThatThrownBy(() -> new ClienteServiceImpl(clienteRepository, cuentaRepository, new BigDecimal("-1")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("CURP duplicada -> codigo 1, 409")
    void registrar_curpDuplicada() {
        when(clienteRepository.existsByCurp("PELJ950520HDFRRN01")).thenReturn(true);

        esError(clienteService.registrar(altaValida()), HttpStatus.CONFLICT, ClienteServiceImpl.MSG_CURP_DUPLICADA);
        verify(clienteRepository, never()).save(any());
    }

    @Test
    @DisplayName("RFC duplicado -> codigo 1, 409")
    void registrar_rfcDuplicado() {
        when(clienteRepository.existsByRfc("PELJ950520AB1")).thenReturn(true);

        esError(clienteService.registrar(altaValida()), HttpStatus.CONFLICT, ClienteServiceImpl.MSG_RFC_DUPLICADO);
    }

    @Test
    @DisplayName("Correo duplicado -> codigo 1, 409")
    void registrar_correoDuplicado() {
        when(clienteRepository.existsByCorreoElectronico(CORREO)).thenReturn(true);

        esError(clienteService.registrar(altaValida()), HttpStatus.CONFLICT, ClienteServiceImpl.MSG_CORREO_DUPLICADO);
        verify(clienteRepository, never()).save(any());
    }

    @Test
    @DisplayName("Usuario duplicado -> codigo 1, 409")
    void registrar_usuarioDuplicado() {
        when(clienteRepository.existsByNombreUsuario("juanperez95")).thenReturn(true);

        esError(clienteService.registrar(altaValida()), HttpStatus.CONFLICT, ClienteServiceImpl.MSG_USUARIO_DUPLICADO);
    }

    // ======================== Consulta y búsqueda ========================

    @Test
    @DisplayName("Consulta por llave compuesta nombre + correo")
    void consultar_existente() {
        existe(clienteExistente(true));
        ClienteConsultaRequest req = new ClienteConsultaRequest();
        req.setNombre(NOMBRE);
        req.setCorreoElectronico("JUAN.PEREZ@correo.com");

        ApiResponse<ClienteResponse> res = clienteService.consultar(req);

        assertThat(res.getCodigo()).isEqualTo(ApiResponse.EXITO);
        assertThat(res.getDatos().getNumeroCuenta()).isEqualTo("1234567890");
    }

    @Test
    @DisplayName("Sin coincidencia de la llave compuesta -> codigo 1, 404")
    void consultar_inexistente() {
        when(clienteRepository.findByNombreIgnoreCaseAndCorreoElectronico(any(), any())).thenReturn(Optional.empty());
        ClienteConsultaRequest req = new ClienteConsultaRequest();
        req.setNombre("Pedro");
        req.setCorreoElectronico(CORREO);

        esError(clienteService.consultar(req), HttpStatus.NOT_FOUND, ClienteServiceImpl.MSG_NO_ENCONTRADO);
    }

    @Test
    @DisplayName("La búsqueda se resuelve en BD con Specification y paginación")
    @SuppressWarnings("unchecked")
    void buscar_paginado() {
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        Page<Cliente> pagina = new PageImpl<>(List.of(clienteExistente(true)), PageRequest.of(1, 5), 6);
        when(clienteRepository.findAll(any(Specification.class), pageable.capture())).thenReturn(pagina);

        ClienteBusquedaRequest filtro = new ClienteBusquedaRequest();
        filtro.setNombre("juan");
        filtro.setActivo(true);
        filtro.setPagina(1);
        filtro.setTamanio(5);

        PaginaResponse<ClienteResponse> res = clienteService.buscar(filtro).getDatos();

        assertThat(pageable.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(5);
        assertThat(res.getTotalElementos()).isEqualTo(6);
        assertThat(res.getTotalPaginas()).isEqualTo(2);
        assertThat(res.getContenido()).hasSize(1);
    }

    @Test
    @DisplayName("El listado general usa la primera página de 20")
    @SuppressWarnings("unchecked")
    void listar_primeraPagina() {
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        when(clienteRepository.findAll(any(Specification.class), pageable.capture())).thenReturn(Page.empty());

        ApiResponse<PaginaResponse<ClienteResponse>> res = clienteService.listar();

        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(20);
        assertThat(res.getMensaje()).isEqualTo("No se encontraron clientes con los criterios proporcionados.");
    }

    // ======================== Actualización parcial ========================

    @Test
    @DisplayName("Solo modifica los campos enviados; los omitidos se conservan")
    void actualizar_parcial() {
        existe(clienteExistente(true));
        guardarDevuelveArgumento();
        ClienteUpdateRequest req = actualizacion();
        req.setTelefonoMovil("4779876543");
        DomicilioUpdateRequest dom = new DomicilioUpdateRequest();
        dom.setCodigoPostal("01000");
        req.setDomicilio(dom);

        ClienteResponse res = clienteService.actualizar(req).getDatos();

        assertThat(res.getTelefonoMovil()).isEqualTo("4779876543");
        assertThat(res.getDomicilio().getCodigoPostal()).isEqualTo("01000");
        assertThat(res.getDomicilio().getCalle()).isEqualTo("Av. Reforma");
        assertThat(res.getApellidoPaterno()).isEqualTo("Pérez");
        assertThat(res.getTelefonoAlternativo()).isEqualTo("4770000000");
        assertThat(res.getCurp()).isEqualTo("PELJ950520HDFRRN01");
        assertThat(res.getNumeroCuenta()).isEqualTo("1234567890");
    }

    @Test
    @DisplayName("null explícito en un campo opcional lo borra; omitido lo conserva")
    void actualizar_nullExplicitoVsOmitido() {
        existe(clienteExistente(true));
        guardarDevuelveArgumento();
        ClienteUpdateRequest req = actualizacion();
        req.setTelefonoAlternativo(Optional.empty());   // "telefonoAlternativo": null

        ClienteResponse res = clienteService.actualizar(req).getDatos();

        assertThat(res.getTelefonoAlternativo()).isNull();
        assertThat(res.getTelefonoMovil()).isEqualTo("4771234567");
    }

    @Test
    @DisplayName("Permite cambiar el correo si el nuevo no pertenece a otro cliente")
    void actualizar_nuevoCorreo() {
        existe(clienteExistente(true));
        guardarDevuelveArgumento();
        ClienteUpdateRequest req = actualizacion();
        req.setNuevoCorreoElectronico("Juan.Nuevo@Correo.com");

        assertThat(clienteService.actualizar(req).getDatos().getCorreoElectronico()).isEqualTo("juan.nuevo@correo.com");
    }

    @Test
    @DisplayName("Rechaza un nuevo correo que ya usa otro cliente -> codigo 1, 409")
    void actualizar_nuevoCorreoDuplicado() {
        existe(clienteExistente(true));
        when(clienteRepository.existsByCorreoElectronicoAndIdNot(eq("otro@correo.com"), eq(1))).thenReturn(true);
        ClienteUpdateRequest req = actualizacion();
        req.setNuevoCorreoElectronico("otro@correo.com");

        esError(clienteService.actualizar(req), HttpStatus.CONFLICT, ClienteServiceImpl.MSG_CORREO_DUPLICADO);
        verify(clienteRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Actualizar a un cliente inexistente -> codigo 1, 404")
    void actualizar_inexistente() {
        when(clienteRepository.findByNombreIgnoreCaseAndCorreoElectronico(any(), any())).thenReturn(Optional.empty());
        ClienteUpdateRequest req = actualizacion();
        req.setTelefonoMovil("4779876543");

        esError(clienteService.actualizar(req), HttpStatus.NOT_FOUND, ClienteServiceImpl.MSG_NO_ENCONTRADO);
    }

    @Test
    @DisplayName("Un cliente dado de baja no se modifica salvo que se reactive -> 409")
    void actualizar_inactivo() {
        existe(clienteExistente(false));
        ClienteUpdateRequest req = actualizacion();
        req.setTelefonoMovil("4779876543");

        ApiResponse<ClienteResponse> res = clienteService.actualizar(req);

        assertThat(res.getCodigo()).isEqualTo(ApiResponse.ERROR);
        assertThat(res.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        verify(clienteRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("activo = true reactiva al cliente y a su cuenta")
    void actualizar_reactivar() {
        existe(clienteExistente(false));
        guardarDevuelveArgumento();
        ClienteUpdateRequest req = actualizacion();
        req.setActivo(true);

        ClienteResponse res = clienteService.actualizar(req).getDatos();

        assertThat(res.getActivo()).isTrue();
        assertThat(res.getEstatusCuenta()).isEqualTo(EstatusCuenta.ACTIVA);
    }

    // ======================== Eliminación lógica ========================

    @Test
    @DisplayName("Eliminación lógica: cliente inactivo y cuenta INACTIVA, sin borrar registros")
    void eliminar_logico() {
        existe(clienteExistente(true));
        guardarDevuelveArgumento();

        ApiResponse<ClienteResponse> res = clienteService.eliminar(eliminacion());

        assertThat(res.getCodigo()).isEqualTo(ApiResponse.EXITO);
        assertThat(res.getDatos().getActivo()).isFalse();
        assertThat(res.getDatos().getEstatusCuenta()).isEqualTo(EstatusCuenta.INACTIVA);
        verify(clienteRepository, never()).delete(any(Cliente.class));
        verify(clienteRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("Eliminar a un cliente ya dado de baja -> codigo 1, 409")
    void eliminar_yaInactivo() {
        existe(clienteExistente(false));

        esError(clienteService.eliminar(eliminacion()), HttpStatus.CONFLICT, "El cliente ya se encuentra dado de baja.");
    }

    @Test
    @DisplayName("Eliminar a un cliente inexistente -> codigo 1, 404")
    void eliminar_inexistente() {
        when(clienteRepository.findByNombreIgnoreCaseAndCorreoElectronico(any(), any())).thenReturn(Optional.empty());

        esError(clienteService.eliminar(eliminacion()), HttpStatus.NOT_FOUND, ClienteServiceImpl.MSG_NO_ENCONTRADO);
    }
}
