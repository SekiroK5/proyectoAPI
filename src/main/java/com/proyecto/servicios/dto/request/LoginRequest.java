package com.proyecto.servicios.dto.request;

import com.proyecto.servicios.util.Texto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Primer paso del inicio de sesion: usuario o correo electronico + contrasena.
 */
@Getter
@Setter
@NoArgsConstructor
public class LoginRequest {

    @Schema(description = "Nombre de usuario o correo electrónico", example = "juanperez95")
    @NotEmpty(message = "El usuario o correo electrónico es obligatorio.")
    @Size(max = 100, message = "El usuario o correo electrónico no puede exceder 100 caracteres.")
    private String usuario;

    @Schema(example = "Segura#2026")
    @NotEmpty(message = "La contraseña es obligatoria.")
    @Size(max = 64, message = "La contraseña no puede exceder 64 caracteres.")
    private String contrasena;

    public void setUsuario(String usuario) {
        // Usuario y correo se guardan en minusculas
        this.usuario = Texto.minusculas(usuario);
    }
}
