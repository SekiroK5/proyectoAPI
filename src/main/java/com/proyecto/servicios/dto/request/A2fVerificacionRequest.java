package com.proyecto.servicios.dto.request;

import com.proyecto.servicios.util.Texto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;

import static com.proyecto.servicios.validation.ValidacionPatrones.CODIGO_A2F;

/**
 * Segundo paso del inicio de sesion: codigo de 6 digitos de la app autenticadora.
 * Si el cliente acaba de configurar su A2F, este mismo paso la activa.
 */
@Getter
@NoArgsConstructor
public class A2fVerificacionRequest extends A2fTokenRequest {

    @Schema(example = "123456")
    @NotEmpty(message = "El código de verificación es obligatorio.")
    @Pattern(regexp = CODIGO_A2F, message = "El código de verificación debe contener exactamente 6 dígitos.")
    private String codigo;

    public void setCodigo(String codigo) {
        this.codigo = Texto.limpiar(codigo);
    }
}
