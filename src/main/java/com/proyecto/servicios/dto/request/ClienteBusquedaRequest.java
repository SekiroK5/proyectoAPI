package com.proyecto.servicios.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.proyecto.servicios.util.Texto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

import static com.proyecto.servicios.validation.ValidacionPatrones.*;

/**
 * Criterios combinables (AND) de la busqueda dinamica. Un criterio omitido o null se excluye.
 * Coincidencia: {@code nombre} es parcial y no distingue mayusculas (nombres y apellidos);
 * el resto de criterios son exactos.
 */
@Getter
@Setter
@NoArgsConstructor
public class ClienteBusquedaRequest extends PaginacionRequest {

    @Schema(description = "Coincidencia parcial en nombre(s) y apellidos", example = "juan")
    @Size(min = 2, max = 50, message = "El texto a buscar en el nombre debe tener entre 2 y 50 caracteres.")
    @Pattern(regexp = SOLO_LETRAS, message = "El texto a buscar en el nombre solo puede contener letras y espacios.")
    private String nombre;

    @Schema(description = "Coincidencia exacta", example = "juan.perez@correo.com")
    @Email(message = "El correo electrónico no tiene un formato válido.")
    @Size(max = 100, message = "El correo electrónico no puede exceder 100 caracteres.")
    private String correoElectronico;

    @Schema(description = "Coincidencia exacta con el teléfono móvil o el alternativo", example = "4771234567")
    @Pattern(regexp = TELEFONO, message = "El teléfono debe contener exactamente 10 dígitos.")
    private String telefono;

    @Schema(description = "true = activos, false = dados de baja, omitido = todos", example = "true")
    private Boolean activo;

    @Pattern(regexp = CURP, message = "La CURP no cumple con el formato oficial.")
    private String curp;

    @Pattern(regexp = RFC, message = "El RFC no cumple con el formato oficial.")
    private String rfc;

    @Pattern(regexp = NUMERO_CUENTA, message = "El número de cuenta debe contener exactamente 10 dígitos.")
    private String numeroCuenta;

    @Schema(description = "Fecha de registro desde (inclusive)", example = "2026-01-01")
    private LocalDate fechaRegistroDesde;

    @Schema(description = "Fecha de registro hasta (inclusive)", example = "2026-12-31")
    private LocalDate fechaRegistroHasta;

    @JsonIgnore
    @AssertTrue(message = "fechaRegistroDesde no puede ser posterior a fechaRegistroHasta.")
    public boolean isRangoFechasValido() {
        return fechaRegistroDesde == null || fechaRegistroHasta == null || !fechaRegistroDesde.isAfter(fechaRegistroHasta);
    }

    public void setNombre(String nombre) {
        this.nombre = Texto.limpiar(nombre);
    }

    public void setCorreoElectronico(String correoElectronico) {
        this.correoElectronico = Texto.minusculas(correoElectronico);
    }

    public void setTelefono(String telefono) {
        this.telefono = Texto.limpiar(telefono);
    }

    public void setCurp(String curp) {
        this.curp = Texto.mayusculas(curp);
    }

    public void setRfc(String rfc) {
        this.rfc = Texto.mayusculas(rfc);
    }

    public void setNumeroCuenta(String numeroCuenta) {
        this.numeroCuenta = Texto.limpiar(numeroCuenta);
    }
}
