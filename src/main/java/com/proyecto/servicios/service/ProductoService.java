package com.proyecto.servicios.service;

import com.proyecto.servicios.model.producto.ProductoDto;

import java.util.List;

public interface ProductoService {

    /**
     * Obtiene la lista de productos desde la base de datos local.
     * Los productos son actualizados automaticamente una vez al dia via scheduler.
     *
     * @return Lista de productos disponibles
     */
    List<ProductoDto> obtenerProductos();

    /**
     * Llama a la API de PuntoRed para actualizar la lista de productos.
     * Este metodo es invocado por el scheduler configurado en productos.service.cron.
     * IMPORTANTE: No debe llamarse mas de 3 veces al dia (restriccion PuntoRed).
     */
    void actualizarProductosDesdeApi();
}
