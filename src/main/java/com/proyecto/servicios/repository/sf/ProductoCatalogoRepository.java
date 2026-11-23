package com.proyecto.servicios.repository.sf;

import com.proyecto.servicios.entity.sf.ProductoCatalogo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductoCatalogoRepository extends JpaRepository<ProductoCatalogo, Integer> {

    List<ProductoCatalogo> findAllByOrderByServicioAscProductoAsc();
}
