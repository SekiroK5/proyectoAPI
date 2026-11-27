package com.proyecto.servicios.controller;

import com.proyecto.servicios.dto.request.VerificacionReferenciaRequest;
import com.proyecto.servicios.dto.response.ApiResponse;
import com.proyecto.servicios.dto.response.GenericResponse;
import com.proyecto.servicios.dto.response.VerificacionReferenciaResponse;
import com.proyecto.servicios.handler.VerificarReferenciaComando;
import com.proyecto.servicios.handler.VerificarReferenciaHandler;
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

@Tag(name = "Referencias", description = "Verificación de la referencia antes de pagar un servicio")
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Datos no válidos",
        content = @Content(schema = @Schema(implementation = GenericResponse.class)))
@RestController
@RequestMapping(value = "/api/v1/referencias", produces = MediaType.APPLICATION_JSON_VALUE)
@Slf4j
public class ReferenciaController {

    private final VerificarReferenciaHandler verificarReferenciaHandler;

    public ReferenciaController(VerificarReferenciaHandler verificarReferenciaHandler) {
        this.verificarReferenciaHandler = verificarReferenciaHandler;
    }

    @Operation(summary = "Verificar referencia",
            description = "Valida el formato y, si GestoPago lo ofrece para el servicio, consulta verifyReference. "
                    + "Devuelve un folio (vigente 15 min, un solo uso) que se envía como folioVerificacion al pagar. "
                    + "Respuestas: 200, 400, 401, 404, 422 (referencia inválida o producto sin referencia), 429, 503.")
    @PostMapping(value = "/verificacion", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<VerificacionReferenciaResponse>> verificar(@Valid @RequestBody VerificacionReferenciaRequest request,
                                                                                @AuthenticationPrincipal Jwt sesion) {
        log.info("POST /api/v1/referencias/verificacion");
        ApiResponse<VerificacionReferenciaResponse> respuesta = verificarReferenciaHandler.handle(
                new VerificarReferenciaComando(Integer.valueOf(sesion.getSubject()), request));
        return ResponseEntity.status(respuesta.getStatus()).body(respuesta);
    }
}
