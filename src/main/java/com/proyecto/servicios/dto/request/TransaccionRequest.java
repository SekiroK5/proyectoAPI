package com.proyecto.servicios.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.proyecto.servicios.util.Texto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

import static com.proyecto.servicios.validation.ValidacionPatrones.CODIGO_A2F;
import static com.proyecto.servicios.validation.ValidacionPatrones.NUMERO_CUENTA;

/**
 * Pago o recarga con GestoPago. Las longitudes siguen la especificacion de sendTx de PuntoRed:
 * telefono 10 a 20, referencia 1 a 100. Si {@code telefono} y {@code referencia} son obligatorios
 * depende del tipo de producto (tipoFront), por lo que esa regla la valida el handler.
 */
@Getter
@Setter
@NoArgsConstructor
public class TransaccionRequest {

    @Schema(description = "Cuenta de la que se descuenta el pago; debe ser del usuario de la sesión", example = "4152313748382907")
    @NotEmpty(message = "El número de cuenta es obligatorio.")
    @Pattern(regexp = NUMERO_CUENTA, message = "El número de cuenta debe contener exactamente 16 dígitos.")
    private String numeroCuenta;

    @Schema(description = "idServicio del catálogo GET /productosList", example = "133")
    @NotNull(message = "El idServicio es obligatorio.")
    @Positive(message = "El idServicio debe ser mayor a cero.")
    @Max(value = Integer.MAX_VALUE, message = "El idServicio no es válido.")
    @JsonAlias("idSer")
    private Integer idServicio;

    @Schema(description = "idProducto del catálogo GET /productosList", example = "582")
    @NotNull(message = "El idProducto es obligatorio.")
    @Positive(message = "El idProducto debe ser mayor a cero.")
    @JsonAlias("idProd")
    private Integer idProducto;

    @Schema(description = "Recargas: precio del producto. Pago de servicios: importe a pagar.", example = "10.00")
    @NotNull(message = "El monto es obligatorio.")
    @DecimalMin(value = "0.00", inclusive = false, message = "El monto debe ser mayor a cero.")
    @Digits(integer = 10, fraction = 2, message = "El monto admite máximo 10 enteros y 2 decimales.")
    private BigDecimal monto;

    @Schema(description = "Recargas (tipoFront 1): número a recargar o referencia, de 10 a 20 caracteres", example = "5555555551")
    @Pattern(regexp = "^[A-Za-z0-9]{10,20}$", message = "El teléfono debe tener de 10 a 20 caracteres alfanuméricos.")
    private String telefono;

    @Schema(description = "Pago de servicios (tipoFront 2): referencia del recibo, de 1 a 100 caracteres", example = "25720381")
    @Pattern(regexp = "^[A-Za-z0-9-]{1,100}$", message = "La referencia debe tener de 1 a 100 caracteres (letras, números o '-').")
    private String referencia;

    @Schema(description = "Código de 6 dígitos de la app autenticadora (también se acepta como \"2fa\")", example = "123456")
    @NotEmpty(message = "El código 2FA es obligatorio.")
    @Pattern(regexp = CODIGO_A2F, message = "El código 2FA debe contener exactamente 6 dígitos.")
    @JsonAlias({"2fa", "codigo2fa"})
    private String codigoA2F;

    public void setNumeroCuenta(String numeroCuenta) {
        this.numeroCuenta = Texto.limpiar(numeroCuenta);
    }

    public void setTelefono(String telefono) {
        this.telefono = Texto.limpiar(telefono);
    }

    public void setReferencia(String referencia) {
        this.referencia = Texto.limpiar(referencia);
    }

    public void setCodigoA2F(String codigoA2F) {
        this.codigoA2F = Texto.limpiar(codigoA2F);
    }
}
