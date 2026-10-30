package com.proyecto.servicios.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.proyecto.servicios.util.Texto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

import static com.proyecto.servicios.validation.ValidacionPatrones.CODIGO_A2F;
import static com.proyecto.servicios.validation.ValidacionPatrones.NUMERO_CUENTA;

/**
 * Deposito a una cuenta propia. El monto maximo por deposito lo define el sistema
 * ({@code onboarding.deposito.monto-maximo}) y lo valida el handler.
 */
@Getter
@Setter
@NoArgsConstructor
public class DepositoRequest {

    @Schema(description = "Cuenta que recibe el depósito; debe ser del usuario de la sesión", example = "4152313748382907")
    @NotEmpty(message = "El número de cuenta es obligatorio.")
    @Pattern(regexp = NUMERO_CUENTA, message = "El número de cuenta debe contener exactamente 16 dígitos.")
    private String numeroCuenta;

    @Schema(example = "500.00")
    @NotNull(message = "El monto es obligatorio.")
    @DecimalMin(value = "0.00", inclusive = false, message = "El monto debe ser mayor a cero.")
    @Digits(integer = 10, fraction = 2, message = "El monto admite máximo 10 enteros y 2 decimales.")
    private BigDecimal monto;

    @Schema(description = "Código de 6 dígitos de la app autenticadora (también se acepta como \"2fa\")", example = "123456")
    @NotEmpty(message = "El código 2FA es obligatorio.")
    @Pattern(regexp = CODIGO_A2F, message = "El código 2FA debe contener exactamente 6 dígitos.")
    @JsonAlias({"2fa", "codigo2fa"})
    private String codigoA2F;

    public void setNumeroCuenta(String numeroCuenta) {
        this.numeroCuenta = Texto.limpiar(numeroCuenta);
    }

    public void setCodigoA2F(String codigoA2F) {
        this.codigoA2F = Texto.limpiar(codigoA2F);
    }
}
