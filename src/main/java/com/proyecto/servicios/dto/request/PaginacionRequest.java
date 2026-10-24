package com.proyecto.servicios.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Base de las busquedas paginadas. El tamanio maximo evita consultas costosas cuando no se envian filtros.
 */
@Getter
@Setter
public abstract class PaginacionRequest {

    public static final int PAGINA_DEFAULT = 0;
    public static final int TAMANIO_DEFAULT = 20;

    @Schema(description = "Número de página (inicia en 0)", example = "0", defaultValue = "0")
    @Min(value = 0, message = "La página debe ser mayor o igual a 0.")
    private Integer pagina = PAGINA_DEFAULT;

    @Schema(description = "Registros por página (1 a 100)", example = "20", defaultValue = "20")
    @Min(value = 1, message = "El tamaño de página debe ser al menos 1.")
    @Max(value = 100, message = "El tamaño de página no puede exceder 100.")
    private Integer tamanio = TAMANIO_DEFAULT;

    public Pageable toPageable(Sort orden) {
        return PageRequest.of(pagina != null ? pagina : PAGINA_DEFAULT,
                tamanio != null ? tamanio : TAMANIO_DEFAULT, orden);
    }
}
