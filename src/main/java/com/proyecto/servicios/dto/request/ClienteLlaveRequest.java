package com.proyecto.servicios.dto.request;

import com.proyecto.servicios.util.Texto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;

/**
 * Llave compuesta (nombre + correo electronico) con la que se localiza a un cliente.
 * La comparten la consulta, la actualizacion y la eliminacion. No sustituye al id primario.
 */
@Getter
public abstract class ClienteLlaveRequest {

    @Schema(description = "Nombre (primer nombre) del cliente; no distingue mayúsculas", example = "Juan")
    @NotBlank(message = "El nombre es obligatorio y no puede contener solo espacios.")
    @Size(max = 50, message = "El nombre no puede exceder 50 caracteres.")
    private String nombre;

    @Schema(description = "Correo electrónico registrado del cliente", example = "juan.perez@correo.com")
    @NotBlank(message = "El correo electrónico es obligatorio.")
    @Email(message = "El correo electrónico no tiene un formato válido.")
    @Size(max = 100, message = "El correo electrónico no puede exceder 100 caracteres.")
    private String correoElectronico;

    public void setNombre(String nombre) {
        this.nombre = Texto.limpiar(nombre);
    }

    public void setCorreoElectronico(String correoElectronico) {
        this.correoElectronico = Texto.minusculas(correoElectronico);
    }
}
