package com.proyecto.servicios.controller;

import com.proyecto.servicios.dto.request.TransaccionRequest;
import com.proyecto.servicios.dto.response.ApiResponse;
import com.proyecto.servicios.dto.response.GenericResponse;
import com.proyecto.servicios.dto.response.TransaccionResponse;
import com.proyecto.servicios.handler.SendTxComando;
import com.proyecto.servicios.handler.SendTxHandler;
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

/**
 * Pagos y recargas con GestoPago. El controlador solo valida el DTO, arma el comando con el cliente
 * de la sesion y delega en el handler.
 */
@Tag(name = "Transacciones", description = "Pagos de servicios y recargas a través de GestoPago (sendTx)")
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Datos no válidos",
        content = @Content(schema = @Schema(implementation = GenericResponse.class)))
@RestController
@RequestMapping(value = "/api/v1/transaccion", produces = MediaType.APPLICATION_JSON_VALUE)
@Slf4j
public class TransaccionController {

    private final SendTxHandler sendTxHandler;

    public TransaccionController(SendTxHandler sendTxHandler) {
        this.sendTxHandler = sendTxHandler;
    }

    @Operation(summary = "Realizar pago o recarga",
            description = "Valida cuenta propia y activa, saldo, producto y código 2FA; envía la transacción cifrada a GestoPago. "
                    + "Respuestas: 200 (pago exitoso), 202 (en confirmación), 400, 401, 403, 404, 409 (saldo o 2FA sin configurar), "
                    + "422 (rechazada por GestoPago), 423 (bloqueo por intentos), 503.")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<TransaccionResponse>> enviar(@Valid @RequestBody TransaccionRequest request,
                                                                   @AuthenticationPrincipal Jwt sesion) {
        log.info("POST /api/v1/transaccion");
        // El cliente sale del token de acceso (subject), nunca del cuerpo de la peticion
        SendTxComando comando = new SendTxComando(Integer.valueOf(sesion.getSubject()), request);
        ApiResponse<TransaccionResponse> respuesta = sendTxHandler.handle(comando);
        return ResponseEntity.status(respuesta.getStatus()).body(respuesta);
    }
}
