package com.proyecto.servicios.dto.response;

import lombok.Getter;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Pagina de resultados con sus metadatos, independiente de la clase Page de Spring.
 */
@Getter
public class PaginaResponse<T> {

    private final List<T> contenido;
    private final int pagina;
    private final int tamanio;
    private final long totalElementos;
    private final int totalPaginas;

    private PaginaResponse(List<T> contenido, int pagina, int tamanio, long totalElementos, int totalPaginas) {
        this.contenido = contenido;
        this.pagina = pagina;
        this.tamanio = tamanio;
        this.totalElementos = totalElementos;
        this.totalPaginas = totalPaginas;
    }

    public static <E, T> PaginaResponse<T> of(Page<E> page, Function<E, T> mapeo) {
        return new PaginaResponse<>(page.map(mapeo).getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
