package com.proyecto.servicios.handler;

import com.proyecto.servicios.dto.response.ApiResponse;

/**
 * Caso de uso encapsulado: recibe una entrada ya validada por el controlador y devuelve la respuesta
 * uniforme de la API. Igual que los servicios, los errores de negocio no se lanzan como excepciones:
 * vienen en la respuesta con codigo 1 y su codigo HTTP.
 *
 * @param <I> entrada del caso de uso
 * @param <O> contenido de {@code datos} en la respuesta
 */
public interface Handler<I, O> {

    ApiResponse<O> handle(I entrada);
}
