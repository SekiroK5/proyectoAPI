package com.proyecto.servicios.controller;

import com.proyecto.servicios.dto.request.ClienteUpdateRequest;
import com.proyecto.servicios.dto.response.ApiResponse;
import com.proyecto.servicios.dto.response.ClienteResponse;
import com.proyecto.servicios.dto.response.CuentaResponse;
import com.proyecto.servicios.dto.response.PaginaResponse;
import com.proyecto.servicios.enums.Catalogos.EstatusCuenta;
import com.proyecto.servicios.exception.GlobalExceptionHandler;
import com.proyecto.servicios.service.ClienteService;
import com.proyecto.servicios.service.CuentaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Capa web: rutas /api/v1, validaciones declarativas (@Valid) resueltas por GlobalExceptionHandler
 * y codigos HTTP tomados de la respuesta del servicio.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("API REST /api/v1 de clientes y cuentas")
class ClienteControllerTest {

    private static final String BASE = "/api/v1/clientes";

    @Mock
    private ClienteService clienteService;

    @Mock
    private CuentaService cuentaService;

    private MockMvc mockMvc;

    private static final String ALTA_VALIDA = """
            {
              "nombre": "Juan", "apellidoPaterno": "Pérez", "apellidoMaterno": "López",
              "fechaNacimiento": "1995-05-20", "curp": "PELJ950520HDFRRN01", "rfc": "PELJ950520AB1",
              "sexo": "M", "nacionalidad": "Mexicana", "estadoCivil": "SOLTERO",
              "correoElectronico": "juan.perez@correo.com", "nombreUsuario": "juanperez95",
              "telefonoMovil": "4771234567", "ocupacion": "Desarrollador", "empresa": "Tech Corp",
              "ingresoMensual": 25000.00,
              "domicilio": { "calle": "Reforma", "numeroExterior": "123", "colonia": "Centro",
                             "municipio": "Cuauhtémoc", "estado": "CDMX", "codigoPostal": "06000", "pais": "México" }
            }
            """;

    private static final String LLAVE = "{\"nombre\":\"Juan\",\"correoElectronico\":\"juan.perez@correo.com\"}";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ClienteController(clienteService), new CuentaController(cuentaService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private static ClienteResponse cliente() {
        ClienteResponse r = new ClienteResponse();
        r.setId(1);
        r.setNombre("Juan");
        r.setCorreoElectronico("juan.perez@correo.com");
        r.setNumeroCuenta("1234567890");
        r.setEstatusCuenta(EstatusCuenta.ACTIVA);
        r.setActivo(true);
        return r;
    }

    // ======================== Alta ========================

    @Test
    @DisplayName("POST valido -> 201 con codigo 0, mensaje y datos")
    void registrar_201() throws Exception {
        when(clienteService.registrar(any()))
                .thenReturn(ApiResponse.exito(HttpStatus.CREATED, "Cliente registrado correctamente.", cliente()));

        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(ALTA_VALIDA))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.codigo").value(0))
                .andExpect(jsonPath("$.mensaje").value("Cliente registrado correctamente."))
                .andExpect(jsonPath("$.datos.numeroCuenta").value("1234567890"))
                .andExpect(jsonPath("$.status").doesNotExist());
    }

    @Test
    @DisplayName("POST invalido -> 400, codigo 1 y los mensajes de cada campo")
    void registrar_400() throws Exception {
        String invalido = ALTA_VALIDA
                .replace("\"Juan\"", "\"   \"")
                .replace("PELJ950520HDFRRN01", "XXXX")
                .replace("4771234567", "477123")
                .replace("06000", "060")
                .replace("25000.00", "0")
                .replace("juan.perez@correo.com", "correo-invalido")
                .replace("1995-05-20", LocalDate.now().minusYears(10).toString());

        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(invalido))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value(1))
                .andExpect(jsonPath("$.mensaje").value(allOf(
                        containsString("Datos no validos"),
                        containsString("nombre="),
                        containsString("telefonoMovil=El teléfono debe contener exactamente 10 dígitos."),
                        containsString("domicilio.codigoPostal="),
                        containsString("ingresoMensual=El ingreso mensual debe ser mayor a cero."),
                        containsString("curp="),
                        containsString("mayor de edad"))));
        verify(clienteService, never()).registrar(any());
    }

    @Test
    @DisplayName("POST intentando fijar id, fechaCreacion o saldo: se ignoran (campos del servidor)")
    void registrar_camposServidorIgnorados() throws Exception {
        when(clienteService.registrar(any()))
                .thenReturn(ApiResponse.exito(HttpStatus.CREATED, "Cliente registrado correctamente.", cliente()));
        String conCamposServidor = ALTA_VALIDA.replace("{\n  \"nombre\"",
                "{\n  \"id\": 99, \"fechaCreacion\": \"2000-01-01T00:00:00\", \"saldo\": 1000000, \"activo\": false, \"nombre\"");

        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(conCamposServidor))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Error de negocio del servicio (correo duplicado) -> 409 con codigo 1")
    void registrar_409() throws Exception {
        when(clienteService.registrar(any())).thenReturn(ApiResponse.error(HttpStatus.CONFLICT,
                "El correo ya se encuentra registrado. Intente con otro correo."));

        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(ALTA_VALIDA))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value(1))
                .andExpect(jsonPath("$.mensaje").value("El correo ya se encuentra registrado. Intente con otro correo."))
                .andExpect(jsonPath("$.datos").doesNotExist());
    }

    @Test
    @DisplayName("POST con enum invalido -> 400")
    void registrar_enumInvalido() throws Exception {
        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                        .content(ALTA_VALIDA.replace("\"SOLTERO\"", "\"COMPLICADO\"")))
                .andExpect(status().isBadRequest());
        verify(clienteService, never()).registrar(any());
    }

    // ======================== Consultas ========================

    @Test
    @DisplayName("GET listado -> 200 con pagina")
    void listar_200() throws Exception {
        when(clienteService.listar()).thenReturn(ApiResponse.exito(HttpStatus.OK, "Se encontraron 1 cliente(s).",
                PaginaResponse.of(new PageImpl<>(List.of(cliente())), r -> r)));

        mockMvc.perform(get(BASE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.contenido[0].id").value(1))
                .andExpect(jsonPath("$.datos.totalElementos").value(1));
    }

    @Test
    @DisplayName("POST /busqueda con tamaño de página excesivo -> 400")
    void buscar_tamanioExcesivo() throws Exception {
        mockMvc.perform(post(BASE + "/busqueda").contentType(MediaType.APPLICATION_JSON).content("{\"tamanio\":500}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(containsString("tamanio=El tamaño de página no puede exceder 100.")));
    }

    @Test
    @DisplayName("POST /busqueda con rango de fechas invertido -> 400")
    void buscar_rangoInvalido() throws Exception {
        mockMvc.perform(post(BASE + "/busqueda").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fechaRegistroDesde\":\"2026-02-01\",\"fechaRegistroHasta\":\"2026-01-01\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(containsString("rangoFechasValido=")));
    }

    @Test
    @DisplayName("POST /consulta sin correo -> 400")
    void consulta_sinCorreo() throws Exception {
        mockMvc.perform(post(BASE + "/consulta").contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"Juan\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(containsString("correoElectronico=El correo electrónico es obligatorio.")));
    }

    @Test
    @DisplayName("POST /consulta sin coincidencia -> 404 con codigo 1")
    void consulta_404() throws Exception {
        when(clienteService.consultar(any())).thenReturn(ApiResponse.error(HttpStatus.NOT_FOUND,
                "No existe un cliente con el nombre y correo electrónico proporcionados."));

        mockMvc.perform(post(BASE + "/consulta").contentType(MediaType.APPLICATION_JSON).content(LLAVE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value(1))
                .andExpect(jsonPath("$.mensaje").value("No existe un cliente con el nombre y correo electrónico proporcionados."));
    }

    // ======================== Actualización ========================

    @Test
    @DisplayName("PATCH distingue null explicito de campo omitido")
    void actualizar_nullVsOmitido() throws Exception {
        ArgumentCaptor<ClienteUpdateRequest> captor = ArgumentCaptor.forClass(ClienteUpdateRequest.class);
        when(clienteService.actualizar(captor.capture()))
                .thenReturn(ApiResponse.exito(HttpStatus.OK, "Cliente actualizado correctamente.", cliente()));

        mockMvc.perform(patch(BASE + "/actualizacion").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Juan\",\"correoElectronico\":\"juan.perez@correo.com\",\"telefonoAlternativo\":null}"))
                .andExpect(status().isOk());

        ClienteUpdateRequest req = captor.getValue();
        assertThat(req.getTelefonoAlternativo()).isEqualTo(Optional.empty()); // null explicito
        assertThat(req.getSegundoNombre()).isNull();                          // omitido
    }

    @Test
    @DisplayName("PATCH sin campos a modificar -> 400")
    void actualizar_sinCambios() throws Exception {
        mockMvc.perform(patch(BASE + "/actualizacion").contentType(MediaType.APPLICATION_JSON).content(LLAVE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(containsString("conCambios=Debe indicar al menos un campo a modificar.")));
    }

    @Test
    @DisplayName("PATCH intentando modificar CURP, RFC o número de cuenta -> 400")
    void actualizar_camposNoModificables() throws Exception {
        mockMvc.perform(patch(BASE + "/actualizacion").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Juan\",\"correoElectronico\":\"juan.perez@correo.com\","
                                + "\"curp\":\"PELJ950520HDFRRN01\",\"rfc\":\"X\",\"numeroCuenta\":\"1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(allOf(
                        containsString("curp=La CURP no puede modificarse."),
                        containsString("rfc=El RFC no puede modificarse."),
                        containsString("numeroCuenta=El número de cuenta no puede modificarse."))));
        verify(clienteService, never()).actualizar(any());
    }

    @Test
    @DisplayName("PATCH con teléfono alternativo inválido dentro de Optional -> 400")
    void actualizar_optionalInvalido() throws Exception {
        mockMvc.perform(patch(BASE + "/actualizacion").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Juan\",\"correoElectronico\":\"juan.perez@correo.com\",\"telefonoAlternativo\":\"123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(containsString("El teléfono alternativo debe contener exactamente 10 dígitos.")));
    }

    // ======================== Eliminación y cuentas ========================

    @Test
    @DisplayName("DELETE /eliminacion -> 200 con confirmación")
    void eliminar_200() throws Exception {
        when(clienteService.eliminar(any()))
                .thenReturn(ApiResponse.exito(HttpStatus.OK, "Cliente eliminado correctamente (baja lógica).", cliente()));

        mockMvc.perform(delete(BASE + "/eliminacion").contentType(MediaType.APPLICATION_JSON).content(LLAVE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value("Cliente eliminado correctamente (baja lógica)."));
    }

    @Test
    @DisplayName("POST /cuentas/consulta inexistente -> 404 con codigo 1")
    void cuenta_404() throws Exception {
        when(cuentaService.consultar(any()))
                .thenReturn(ApiResponse.<CuentaResponse>error(HttpStatus.NOT_FOUND, "No existe una cuenta con el número 0000000000."));

        mockMvc.perform(post("/api/v1/cuentas/consulta").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"numeroCuenta\":\"0000000000\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value(1));
    }

    @Test
    @DisplayName("POST /cuentas/consulta con número inválido -> 400")
    void cuenta_numeroInvalido() throws Exception {
        mockMvc.perform(post("/api/v1/cuentas/consulta").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"numeroCuenta\":\"12ab\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(containsString("numeroCuenta=El número de cuenta debe contener exactamente 10 dígitos.")));
    }
}
