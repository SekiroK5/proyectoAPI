package com.proyecto.servicios.dto.response;

import com.proyecto.servicios.enums.Catalogos.MetodoA2F;
import com.proyecto.servicios.enums.Catalogos.PasoLogin;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Resultado de validar la contrasena: indica el siguiente paso y entrega el token temporal para darlo.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

    private PasoLogin siguientePaso;
    private MetodoA2F metodoA2F;
    private String tokenTemporal;
    private Long expiraEnSegundos;
}
