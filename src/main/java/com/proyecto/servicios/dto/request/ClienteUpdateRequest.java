package com.proyecto.servicios.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
import java.util.Optional;
import java.util.stream.Stream;

import static com.proyecto.servicios.validation.ValidacionPatrones.*;

/**
 * Actualizacion parcial (PATCH). {@code nombre} y {@code correoElectronico} (heredados) son la llave
 * con los valores ACTUALES del cliente; para cambiarlos se usan {@code nuevoNombre} y {@code nuevoCorreoElectronico}.
 * <p>
 * Reglas para valores omitidos y {@code null}:
 * <ul>
 *   <li>Campos obligatorios del negocio (String, enum, fecha, numero): omitido o {@code null} significa
 *       "sin cambio"; nunca se sobrescriben con null.</li>
 *   <li>Campos opcionales del negocio ({@code segundoNombre}, {@code telefonoAlternativo},
 *       {@code domicilio.numeroInterior}) usan {@link Optional}: omitido = sin cambio,
 *       {@code null} explicito = se borra el valor.</li>
 * </ul>
 * CURP, RFC y numero de cuenta no son modificables: si se envian, la solicitud se rechaza.
 */
@Getter
@Setter
@NoArgsConstructor
public class ClienteUpdateRequest extends ClienteLlaveRequest {

    // ---------------- Cambio de la llave ----------------

    @Schema(description = "Nuevo nombre (opcional)", example = "Juan")
    @Size(min = 2, max = 50, message = "El nuevo nombre debe tener entre 2 y 50 caracteres.")
    @Pattern(regexp = SOLO_LETRAS, message = "El nuevo nombre solo puede contener letras y espacios.")
    private String nuevoNombre;

    @Schema(description = "Nuevo correo electrónico (opcional); debe ser único", example = "juan.nuevo@correo.com")
    @Email(message = "El nuevo correo electrónico no tiene un formato válido.")
    @Pattern(regexp = CORREO, message = "El nuevo correo electrónico no tiene un formato válido.")
    @Size(max = 100, message = "El nuevo correo electrónico no puede exceder 100 caracteres.")
    private String nuevoCorreoElectronico;

    // ---------------- Datos personales ----------------

    @Schema(type = "string", nullable = true, description = "Omitido: sin cambio. null: se elimina el valor.", example = "Carlos")
    private Optional<@Size(min = 2, max = 50, message = "El segundo nombre debe tener entre 2 y 50 caracteres.")
            @Pattern(regexp = SOLO_LETRAS, message = "El segundo nombre solo puede contener letras y espacios.") String> segundoNombre;

    @Size(min = 2, max = 50, message = "El apellido paterno debe tener entre 2 y 50 caracteres.")
    @Pattern(regexp = SOLO_LETRAS, message = "El apellido paterno solo puede contener letras y espacios.")
    private String apellidoPaterno;

    @Size(min = 2, max = 50, message = "El apellido materno debe tener entre 2 y 50 caracteres.")
    @Pattern(regexp = SOLO_LETRAS, message = "El apellido materno solo puede contener letras y espacios.")
    private String apellidoMaterno;

    @Past(message = "La fecha de nacimiento no puede ser una fecha futura.")
    @MayorDeEdad
    private LocalDate fechaNacimiento;

    private Sexo sexo;

    @Size(min = 2, max = 50, message = "La nacionalidad debe tener entre 2 y 50 caracteres.")
    @Pattern(regexp = SOLO_LETRAS, message = "La nacionalidad solo puede contener letras y espacios.")
    private String nacionalidad;

    private EstadoCivil estadoCivil;

    // ---------------- Datos de contacto ----------------

    @Schema(example = "4779876543")
    @Pattern(regexp = TELEFONO, message = "El teléfono debe contener exactamente 10 dígitos.")
    private String telefonoMovil;

    @Schema(type = "string", nullable = true, description = "Omitido: sin cambio. null: se elimina el valor.", example = "4771112233")
    private Optional<@Pattern(regexp = TELEFONO, message = "El teléfono alternativo debe contener exactamente 10 dígitos.") String> telefonoAlternativo;

    // ---------------- Domicilio ----------------

    @Valid
    private DomicilioUpdateRequest domicilio;

    // ---------------- Informacion laboral ----------------

    @Size(min = 1, max = 100, message = "La ocupación debe tener entre 1 y 100 caracteres.")
    @Pattern(regexp = TEXTO_NO_VACIO, message = "La ocupación no puede contener solo espacios.")
    private String ocupacion;

    @Size(min = 1, max = 100, message = "La empresa debe tener entre 1 y 100 caracteres.")
    @Pattern(regexp = TEXTO_NO_VACIO, message = "La empresa no puede contener solo espacios.")
    private String empresa;

    @DecimalMin(value = "0.00", inclusive = false, message = "El ingreso mensual debe ser mayor a cero.")
    @Digits(integer = 10, fraction = 2, message = "El ingreso mensual admite máximo 10 enteros y 2 decimales.")
    private BigDecimal ingresoMensual;

    // ---------------- Estado ----------------

    @Schema(description = "true reactiva al cliente y su cuenta; false equivale a la baja lógica", example = "true")
    private Boolean activo;

    // ---------------- Campos NO modificables ----------------

    @Schema(hidden = true)
    @Null(message = "La CURP no puede modificarse.")
    private String curp;

    @Schema(hidden = true)
    @Null(message = "El RFC no puede modificarse.")
    private String rfc;

    @Schema(hidden = true)
    @Null(message = "El número de cuenta no puede modificarse.")
    private String numeroCuenta;

    @JsonIgnore
    @AssertTrue(message = "Debe indicar al menos un campo a modificar.")
    public boolean isConCambios() {
        return segundoNombre != null || telefonoAlternativo != null
                || (domicilio != null && domicilio.tieneCambios())
                || Stream.of(nuevoNombre, nuevoCorreoElectronico, apellidoPaterno, apellidoMaterno, fechaNacimiento,
                        sexo, nacionalidad, estadoCivil, telefonoMovil, ocupacion, empresa, ingresoMensual, activo)
                .anyMatch(v -> v != null);
    }

    public void setNuevoNombre(String nuevoNombre) {
        this.nuevoNombre = Texto.limpiar(nuevoNombre);
    }

    public void setNuevoCorreoElectronico(String nuevoCorreoElectronico) {
        this.nuevoCorreoElectronico = Texto.minusculas(nuevoCorreoElectronico);
    }
}
