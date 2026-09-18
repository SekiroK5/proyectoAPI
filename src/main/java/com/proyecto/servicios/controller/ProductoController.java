package com.proyecto.servicios.controller;

import com.proyecto.servicios.model.producto.ProductoDto;
import com.proyecto.servicios.service.ProductoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/productos")
@Slf4j
public class ProductoController {

    private final ProductoService productoService;

    public ProductoController(ProductoService productoService) {
        this.productoService = productoService;
    }

    /**
     * Retorna la lista de productos/servicios disponibles del distribuidor.
     * Los datos son actualizados automaticamente una vez al dia desde PuntoRed.
     *
     * @return Lista de productos
     */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<ProductoDto>> obtenerProductos() {
        log.info("Solicitud recibida: GET /productos");
        List<ProductoDto> productos = productoService.obtenerProductos();
        log.info("Retornando {} productos", productos.size());
        return ResponseEntity.ok(productos);
    }
}
