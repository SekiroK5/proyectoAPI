package com.proyecto.servicios.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.proyecto.servicios.validation.ValidacionPatrones;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Optional;
import java.util.stream.Stream;

/**
 * Actualizacion parcial del domicilio: un campo omitido no se modifica.
 * {@code numeroInterior} es opcional en el negocio, por eso distingue
 * omitido (sin cambio) de {@code null} explicito (se borra el valor).
 */
@Getter
@Setter
@NoArgsConstructor
public class DomicilioUpdateRequest {

    @Size(min = 1, max = 100, message = "La calle debe tener entre 1 y 100 caracteres.")
    @Pattern(regexp = ValidacionPatrones.TEXTO_NO_VACIO, message = "La calle no puede contener solo espacios.")
    private String calle;

    @Size(min = 1, max = 20, message = "El número exterior debe tener entre 1 y 20 caracteres.")
    @Pattern(regexp = ValidacionPatrones.TEXTO_NO_VACIO, message = "El número exterior no puede contener solo espacios.")
    private String numeroExterior;

    @Schema(type = "string", nullable = true, description = "Omitido: sin cambio. null: se elimina el valor.", example = "Depto 3")
    private Optional<@Size(max = 20, message = "El número interior no puede exceder 20 caracteres.") String> numeroInterior;

    @Size(min = 1, max = 100, message = "La colonia debe tener entre 1 y 100 caracteres.")
    @Pattern(regexp = ValidacionPatrones.TEXTO_NO_VACIO, message = "La colonia no puede contener solo espacios.")
    private String colonia;

    @Size(min = 1, max = 100, message = "El municipio debe tener entre 1 y 100 caracteres.")
    @Pattern(regexp = ValidacionPatrones.TEXTO_NO_VACIO, message = "El municipio no puede contener solo espacios.")
    private String municipio;

    @Size(min = 1, max = 100, message = "El estado debe tener entre 1 y 100 caracteres.")
    @Pattern(regexp = ValidacionPatrones.TEXTO_NO_VACIO, message = "El estado no puede contener solo espacios.")
    private String estado;

    @Pattern(regexp = ValidacionPatrones.CODIGO_POSTAL, message = "El código postal debe contener exactamente 5 dígitos.")
    private String codigoPostal;

    @Size(max = 50, message = "El país no puede exceder 50 caracteres.")
    @Pattern(regexp = ValidacionPatrones.SOLO_LETRAS, message = "El país solo puede contener letras y espacios.")
    private String pais;

    @JsonIgnore
    public boolean tieneCambios() {
        return numeroInterior != null
                || Stream.of(calle, numeroExterior, colonia, municipio, estado, codigoPostal, pais).anyMatch(v -> v != null);
    }
}
