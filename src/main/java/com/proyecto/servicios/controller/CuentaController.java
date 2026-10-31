package com.proyecto.servicios.controller;

import com.proyecto.servicios.dto.request.CuentaBusquedaRequest;
import com.proyecto.servicios.dto.request.CuentaConsultaRequest;
import com.proyecto.servicios.dto.request.DepositoRequest;
import com.proyecto.servicios.dto.response.ApiResponse;
import com.proyecto.servicios.dto.response.CuentaResponse;
import com.proyecto.servicios.dto.response.DepositoResponse;
import com.proyecto.servicios.dto.response.GenericResponse;
import com.proyecto.servicios.dto.response.PaginaResponse;
import com.proyecto.servicios.handler.DepositoComando;
import com.proyecto.servicios.handler.DepositoHandler;
import com.proyecto.servicios.service.CuentaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Cuentas", description = "Consulta de cuentas, saldos y depósitos")
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Datos no válidos",
        content = @Content(schema = @Schema(implementation = GenericResponse.class)))
@RestController
@RequestMapping(value = "/api/v1/cuentas", produces = MediaType.APPLICATION_JSON_VALUE)
@Slf4j
public class CuentaController {

    private final CuentaService cuentaService;
    private final DepositoHandler depositoHandler;

    public CuentaController(CuentaService cuentaService, DepositoHandler depositoHandler) {
        this.cuentaService = cuentaService;
        this.depositoHandler = depositoHandler;
    }

    @Operation(summary = "Consultar cuenta y saldo", description = "Busca la cuenta por su número. Respuestas: 200, 400, 404.")
    @PostMapping(value = "/consulta", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<CuentaResponse>> consultar(@Valid @RequestBody CuentaConsultaRequest request) {
        log.info("POST /api/v1/cuentas/consulta");
        ApiResponse<CuentaResponse> respuesta = cuentaService.consultar(request);
        return ResponseEntity.status(respuesta.getStatus()).body(respuesta);
    }

    @Operation(summary = "Búsqueda dinámica de cuentas", description = "Combina estatus (p. ej. ACTIVA) y CURP del titular. Paginada.")
    @PostMapping(value = "/busqueda", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<PaginaResponse<CuentaResponse>>> buscar(@Valid @RequestBody CuentaBusquedaRequest filtro) {
        log.info("POST /api/v1/cuentas/busqueda");
        ApiResponse<PaginaResponse<CuentaResponse>> respuesta = cuentaService.buscar(filtro);
        return ResponseEntity.status(respuesta.getStatus()).body(respuesta);
    }

    @Operation(summary = "Depositar a mi cuenta",
            description = "Abona saldo a una cuenta propia con autorización 2FA. Respuestas: 200, 400 (monto inválido o mayor al máximo), "
                    + "401 (2FA incorrecto), 403 (cuenta ajena o inactiva), 404, 409 (2FA sin configurar o en uso), 423 (bloqueo por intentos).")
    @PostMapping(value = "/deposito", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<DepositoResponse>> depositar(@Valid @RequestBody DepositoRequest request,
                                                                   @AuthenticationPrincipal Jwt sesion) {
        log.info("POST /api/v1/cuentas/deposito");
        // El cliente sale del token de acceso (subject), nunca del cuerpo de la peticion
        ApiResponse<DepositoResponse> respuesta = depositoHandler.handle(
                new DepositoComando(Integer.valueOf(sesion.getSubject()), request));
        return ResponseEntity.status(respuesta.getStatus()).body(respuesta);
    }
}
