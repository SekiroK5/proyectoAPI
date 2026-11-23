package com.proyecto.servicios.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class DetalleTransaccionRequest {

    @Schema(description = "id de la transacción (viene en el historial y en el resultado del pago)", example = "12")
    @NotNull(message = "El id de la transacción es obligatorio.")
    @Positive(message = "El id de la transacción debe ser mayor a cero.")
    private Integer id;
}
