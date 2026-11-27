package com.proyecto.servicios.service.impl;

import com.proyecto.servicios.entity.sf.ProductoCatalogo;
import com.proyecto.servicios.repository.sf.ProductoCatalogoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Lectura y reemplazo del catalogo guardado en la base de datos. El reemplazo es una sola
 * transaccion: quien consulte el catalogo ve la version anterior o la nueva, nunca una mezcla.
 */
@Service
public class CatalogoProductos {

    private final ProductoCatalogoRepository repository;

    public CatalogoProductos(ProductoCatalogoRepository repository) {
        this.repository = repository;
    }

    @Transactional(value = "sfTransactionManager", readOnly = true)
    public List<ProductoCatalogo> listar() {
        return repository.findAllByOrderByServicioAscProductoAsc();
    }

    @Transactional(value = "sfTransactionManager", readOnly = true)
    public boolean estaVacio() {
        return repository.count() == 0;
    }

    @Transactional("sfTransactionManager")
    public void reemplazar(List<ProductoCatalogo> productos) {
        repository.deleteAllInBatch();
        repository.flush();
        repository.saveAll(productos);
    }
}
