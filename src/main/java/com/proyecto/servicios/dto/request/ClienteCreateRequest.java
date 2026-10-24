package com.proyecto.servicios.dto.request;

import com.proyecto.servicios.enums.Catalogos.EstadoCivil;
import com.proyecto.servicios.enums.Catalogos.Sexo;
import com.proyecto.servicios.util.Texto;
import com.proyecto.servicios.validation.MayorDeEdad;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

import static com.proyecto.servicios.validation.ValidacionPatrones.*;

/**
 * Alta de cliente. No incluye campos controlados por el servidor: id, fecha de creacion,
 * estatus activo, numero de cuenta, saldo inicial y estatus de la cuenta los define el sistema.
 * <p>
 * Criterio de anotaciones: {@code @NotBlank} en texto libre obligatorio; {@code @NotEmpty} cuando un
 * {@code @Pattern} ya impide espacios (CURP, RFC, telefono); {@code @NotNull} en tipos no texto.
 */
@Getter
@Setter
@NoArgsConstructor
public class ClienteCreateRequest {

    // ---------------- Datos personales ----------------

    @Schema(example = "Juan")
    @NotBlank(message = "El nombre es obligatorio y no puede contener solo espacios.")
    @Size(min = 2, max = 50, message = "El nombre debe tener entre 2 y 50 caracteres.")
    @Pattern(regexp = SOLO_LETRAS, message = "El nombre solo puede contener letras y espacios.")
    private String nombre;

    @Schema(description = "Opcional", example = "Carlos")
    @Size(min = 2, max = 50, message = "El segundo nombre debe tener entre 2 y 50 caracteres.")
    @Pattern(regexp = SOLO_LETRAS, message = "El segundo nombre solo puede contener letras y espacios.")
    private String segundoNombre;

    @Schema(example = "Pérez")
    @NotBlank(message = "El apellido paterno es obligatorio y no puede contener solo espacios.")
    @Size(min = 2, max = 50, message = "El apellido paterno debe tener entre 2 y 50 caracteres.")
    @Pattern(regexp = SOLO_LETRAS, message = "El apellido paterno solo puede contener letras y espacios.")
    private String apellidoPaterno;

    @Schema(example = "López")
    @NotBlank(message = "El apellido materno es obligatorio y no puede contener solo espacios.")
    @Size(min = 2, max = 50, message = "El apellido materno debe tener entre 2 y 50 caracteres.")
    @Pattern(regexp = SOLO_LETRAS, message = "El apellido materno solo puede contener letras y espacios.")
    private String apellidoMaterno;

    @Schema(example = "1995-05-20")
    @NotNull(message = "La fecha de nacimiento es obligatoria.")
    @Past(message = "La fecha de nacimiento no puede ser una fecha futura.")
    @MayorDeEdad
    private LocalDate fechaNacimiento;

    @Schema(example = "PELJ950520HDFRRN01")
    @NotEmpty(message = "La CURP es obligatoria.")
    @Size(min = 18, max = 18, message = "La CURP debe contener exactamente 18 caracteres.")
    @Pattern(regexp = CURP, message = "La CURP no cumple con el formato oficial.")
    private String curp;

    @Schema(example = "PELJ950520AB1")
    @NotEmpty(message = "El RFC es obligatorio.")
    @Size(min = 12, max = 13, message = "El RFC debe contener 12 o 13 caracteres.")
    @Pattern(regexp = RFC, message = "El RFC no cumple con el formato oficial.")
    private String rfc;

    @Schema(description = "M = masculino, F = femenino, O = otro", example = "M")
    @NotNull(message = "El sexo es obligatorio (M, F u O).")
    private Sexo sexo;

    @Schema(example = "Mexicana")
    @NotBlank(message = "La nacionalidad es obligatoria.")
    @Size(min = 2, max = 50, message = "La nacionalidad debe tener entre 2 y 50 caracteres.")
    @Pattern(regexp = SOLO_LETRAS, message = "La nacionalidad solo puede contener letras y espacios.")
    private String nacionalidad;

    @Schema(example = "SOLTERO")
    @NotNull(message = "El estado civil es obligatorio.")
    private EstadoCivil estadoCivil;

    // ---------------- Datos de contacto ----------------

    @Schema(example = "juan.perez@correo.com")
    @NotBlank(message = "El correo electrónico es obligatorio.")
    @Email(message = "El correo electrónico no tiene un formato válido.")
    @Pattern(regexp = CORREO, message = "El correo electrónico no tiene un formato válido.")
    @Size(max = 100, message = "El correo electrónico no puede exceder 100 caracteres.")
    private String correoElectronico;

    @Schema(example = "juanperez95")
    @NotEmpty(message = "El nombre de usuario es obligatorio.")
    @Pattern(regexp = "^[a-zA-Z0-9._-]{4,50}$", message = "El nombre de usuario debe tener entre 4 y 50 caracteres (letras, números, '.', '_' o '-').")
    private String nombreUsuario;

    @Schema(example = "4771234567")
    @NotEmpty(message = "El teléfono es obligatorio.")
    @Pattern(regexp = TELEFONO, message = "El teléfono debe contener exactamente 10 dígitos.")
    private String telefonoMovil;

    @Schema(description = "Opcional", example = "4779876543")
    @Pattern(regexp = TELEFONO, message = "El teléfono alternativo debe contener exactamente 10 dígitos.")
    private String telefonoAlternativo;

    // ---------------- Domicilio ----------------

    @NotNull(message = "Los datos del domicilio son obligatorios.")
    @Valid
    private DomicilioRequest domicilio;

    // ---------------- Informacion laboral ----------------

    @Schema(example = "Desarrollador")
    @NotBlank(message = "La ocupación es obligatoria.")
    @Size(max = 100, message = "La ocupación no puede exceder 100 caracteres.")
    private String ocupacion;

    @Schema(example = "Tech Solutions")
    @NotBlank(message = "La empresa es obligatoria.")
    @Size(max = 100, message = "La empresa no puede exceder 100 caracteres.")
    private String empresa;

    @Schema(example = "25000.00")
    @NotNull(message = "El ingreso mensual es obligatorio.")
    @DecimalMin(value = "0.00", inclusive = false, message = "El ingreso mensual debe ser mayor a cero.")
    @Digits(integer = 10, fraction = 2, message = "El ingreso mensual admite máximo 10 enteros y 2 decimales.")
    private BigDecimal ingresoMensual;

    // ---------------- Normalizacion de entrada ----------------

    public void setSegundoNombre(String segundoNombre) {
        this.segundoNombre = Texto.limpiar(segundoNombre);
    }

    public void setCurp(String curp) {
        this.curp = Texto.mayusculas(curp);
    }

    public void setRfc(String rfc) {
        this.rfc = Texto.mayusculas(rfc);
    }

    public void setCorreoElectronico(String correoElectronico) {
        this.correoElectronico = Texto.minusculas(correoElectronico);
    }

    public void setNombreUsuario(String nombreUsuario) {
        this.nombreUsuario = Texto.minusculas(nombreUsuario);
    }

    public void setTelefonoAlternativo(String telefonoAlternativo) {
        this.telefonoAlternativo = Texto.limpiar(telefonoAlternativo);
    }
}
