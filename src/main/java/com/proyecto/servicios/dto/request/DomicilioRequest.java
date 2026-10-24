package com.proyecto.servicios.dto.request;

import com.proyecto.servicios.util.Texto;
import com.proyecto.servicios.validation.ValidacionPatrones;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class DomicilioRequest {

    @Schema(example = "Av. Paseo de la Reforma")
    @NotBlank(message = "La calle es obligatoria.")
    @Size(max = 100, message = "La calle no puede exceder 100 caracteres.")
    private String calle;

    @Schema(example = "222")
    @NotBlank(message = "El número exterior es obligatorio.")
    @Size(max = 20, message = "El número exterior no puede exceder 20 caracteres.")
    private String numeroExterior;

    @Schema(description = "Opcional", example = "Piso 5")
    @Size(max = 20, message = "El número interior no puede exceder 20 caracteres.")
    private String numeroInterior;

    @Schema(example = "Juárez")
    @NotBlank(message = "La colonia es obligatoria.")
    @Size(max = 100, message = "La colonia no puede exceder 100 caracteres.")
    private String colonia;

    @Schema(example = "Cuauhtémoc")
    @NotBlank(message = "El municipio es obligatorio.")
    @Size(max = 100, message = "El municipio no puede exceder 100 caracteres.")
    private String municipio;

    @Schema(example = "Ciudad de México")
    @NotBlank(message = "El estado es obligatorio.")
    @Size(max = 100, message = "El estado no puede exceder 100 caracteres.")
    private String estado;

    // @NotEmpty basta: el patron ya rechaza los espacios
    @Schema(example = "06600")
    @NotEmpty(message = "El código postal es obligatorio.")
    @Pattern(regexp = ValidacionPatrones.CODIGO_POSTAL, message = "El código postal debe contener exactamente 5 dígitos.")
    private String codigoPostal;

    @Schema(example = "México")
    @NotBlank(message = "El país es obligatorio.")
    @Size(max = 50, message = "El país no puede exceder 50 caracteres.")
    @Pattern(regexp = ValidacionPatrones.SOLO_LETRAS, message = "El país solo puede contener letras y espacios.")
    private String pais;

    public void setNumeroInterior(String numeroInterior) {
        this.numeroInterior = Texto.limpiar(numeroInterior);
    }
}
