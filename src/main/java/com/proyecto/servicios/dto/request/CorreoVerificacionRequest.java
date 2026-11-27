package com.proyecto.servicios.dto.request;

import com.proyecto.servicios.util.Texto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;

import static com.proyecto.servicios.validation.ValidacionPatrones.CODIGO_A2F;

/**
 * Confirma el correo con el codigo de 6 digitos que se le envio. Lleva el token temporal del login.
 */
@Getter
@NoArgsConstructor
public class CorreoVerificacionRequest extends A2fTokenRequest {

    @Schema(example = "123456")
    @NotEmpty(message = "El código de verificación es obligatorio.")
    @Pattern(regexp = CODIGO_A2F, message = "El código de verificación debe contener exactamente 6 dígitos.")
    private String codigo;

    public void setCodigo(String codigo) {
        this.codigo = Texto.limpiar(codigo);
    }
}
