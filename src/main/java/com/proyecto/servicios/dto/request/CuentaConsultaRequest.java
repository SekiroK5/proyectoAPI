package com.proyecto.servicios.dto.request;

import com.proyecto.servicios.util.Texto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;

import static com.proyecto.servicios.validation.ValidacionPatrones.NUMERO_CUENTA;

/**
 * Consulta de una cuenta (incluye el saldo) por su numero.
 */
@Getter
@NoArgsConstructor
public class CuentaConsultaRequest {

    @Schema(example = "1234567890")
    @NotEmpty(message = "El número de cuenta es obligatorio.")
    @Pattern(regexp = NUMERO_CUENTA, message = "El número de cuenta debe contener exactamente 10 dígitos.")
    private String numeroCuenta;

    public void setNumeroCuenta(String numeroCuenta) {
        this.numeroCuenta = Texto.limpiar(numeroCuenta);
    }
}
