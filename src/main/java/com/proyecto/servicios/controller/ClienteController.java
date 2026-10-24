package com.proyecto.servicios.controller;

import com.proyecto.servicios.dto.request.ClienteBusquedaRequest;
import com.proyecto.servicios.dto.request.ClienteConsultaRequest;
import com.proyecto.servicios.dto.request.ClienteCreateRequest;
import com.proyecto.servicios.dto.request.ClienteEliminacionRequest;
import com.proyecto.servicios.dto.request.ClienteUpdateRequest;
import com.proyecto.servicios.dto.response.ApiResponse;
import com.proyecto.servicios.dto.response.ClienteResponse;
import com.proyecto.servicios.dto.response.GenericResponse;
import com.proyecto.servicios.dto.response.PaginaResponse;
import com.proyecto.servicios.service.ClienteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Recibe las solicitudes, valida los DTOs con {@code @Valid} y delega en el servicio.
 * Todos los datos llegan en el cuerpo (sin @PathVariable ni @RequestParam); los errores de validacion
 * los atiende GlobalExceptionHandler y los de negocio vienen en la respuesta del servicio (codigo 1).
 */
@Tag(name = "Clientes", description = "Onboarding y administración de clientes personas físicas")
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Datos no válidos",
        content = @Content(schema = @Schema(implementation = GenericResponse.class)))
@RestController
@RequestMapping(value = "/api/v1/clientes", produces = MediaType.APPLICATION_JSON_VALUE)
@Slf4j
public class ClienteController {

    private final ClienteService clienteService;

    public ClienteController(ClienteService clienteService) {
        this.clienteService = clienteService;
    }

    @Operation(summary = "Registrar cliente",
            description = "Registra al cliente y le abre automáticamente una cuenta ACTIVA. Respuestas: 201, 400, 409 (CURP, RFC, correo o usuario duplicado).")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<ClienteResponse>> registrar(@Valid @RequestBody ClienteCreateRequest request) {
        log.info("POST /api/v1/clientes");
        return responder(clienteService.registrar(request));
    }

    @Operation(summary = "Listar clientes",
            description = "Devuelve la primera página (20 registros). Para otras páginas o filtros use POST /busqueda.")
    @GetMapping
    public ResponseEntity<ApiResponse<PaginaResponse<ClienteResponse>>> listar() {
        log.info("GET /api/v1/clientes");
        return responder(clienteService.listar());
    }

    @Operation(summary = "Búsqueda dinámica de clientes",
            description = "Combina (AND) nombre (parcial), correo, teléfono, activo, CURP, RFC, número de cuenta y rango de fechas de registro. Los criterios omitidos se excluyen. Paginada.")
    @PostMapping(value = "/busqueda", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<PaginaResponse<ClienteResponse>>> buscar(@Valid @RequestBody ClienteBusquedaRequest filtro) {
        log.info("POST /api/v1/clientes/busqueda");
        return responder(clienteService.buscar(filtro));
    }

    @Operation(summary = "Consultar cliente por llave compuesta",
            description = "Localiza al cliente cuyo nombre y correo electrónico coinciden. Respuestas: 200, 400, 404.")
    @PostMapping(value = "/consulta", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<ClienteResponse>> consultar(@Valid @RequestBody ClienteConsultaRequest request) {
        log.info("POST /api/v1/clientes/consulta");
        return responder(clienteService.consultar(request));
    }

    @Operation(summary = "Actualizar cliente (parcial)",
            description = "nombre y correoElectronico identifican al cliente; solo se modifican los campos enviados. "
                    + "Para cambiar la llave use nuevoNombre / nuevoCorreoElectronico. CURP, RFC y número de cuenta no son modificables. "
                    + "Respuestas: 200, 400, 404, 409.")
    @PatchMapping(value = "/actualizacion", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<ClienteResponse>> actualizar(@Valid @RequestBody ClienteUpdateRequest request) {
        log.info("PATCH /api/v1/clientes/actualizacion");
        return responder(clienteService.actualizar(request));
    }

    @Operation(summary = "Eliminar cliente (baja lógica)",
            description = "Marca al cliente como inactivo e inactiva su cuenta; los registros se conservan. Respuestas: 200, 400, 404, 409.")
    @DeleteMapping(value = "/eliminacion", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<ClienteResponse>> eliminar(@Valid @RequestBody ClienteEliminacionRequest request) {
        log.info("DELETE /api/v1/clientes/eliminacion");
        return responder(clienteService.eliminar(request));
    }

    private static <T> ResponseEntity<ApiResponse<T>> responder(ApiResponse<T> respuesta) {
        return ResponseEntity.status(respuesta.getStatus()).body(respuesta);
    }
}
