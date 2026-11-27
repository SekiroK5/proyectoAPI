package com.proyecto.servicios.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.proyecto.servicios.util.Texto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Referencia a verificar antes de pagar un servicio. El formato especifico (p. ej. 10 digitos
 * cuando la referencia es un telefono) depende del producto y lo valida el handler.
 */
@Getter
@Setter
@NoArgsConstructor
public class VerificacionReferenciaRequest {

    @Schema(description = "idServicio del catálogo", example = "108")
    @NotNull(message = "El idServicio es obligatorio.")
    @Positive(message = "El idServicio debe ser mayor a cero.")
    @JsonAlias("idSer")
    private Integer idServicio;

    @Schema(description = "idProducto del catálogo", example = "272")
    @NotNull(message = "El idProducto es obligatorio.")
    @Positive(message = "El idProducto debe ser mayor a cero.")
    @JsonAlias("idProd")
    private Integer idProducto;

    @Schema(description = "Referencia del recibo: número de cliente, contrato, teléfono o código de barras", example = "5610440665")
    @NotEmpty(message = "La referencia es obligatoria.")
    @Pattern(regexp = "^[A-Za-z0-9-]{1,100}$", message = "La referencia debe tener de 1 a 100 caracteres (letras, números o '-').")
    private String referencia;

    public void setReferencia(String referencia) {
        this.referencia = Texto.limpiar(referencia);
    }
}
