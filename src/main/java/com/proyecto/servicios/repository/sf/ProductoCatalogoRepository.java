package com.proyecto.servicios.repository.sf;

import com.proyecto.servicios.entity.sf.ProductoCatalogo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductoCatalogoRepository extends JpaRepository<ProductoCatalogo, Integer> {

    List<ProductoCatalogo> findAllByOrderByServicioAscProductoAsc();

    Optional<ProductoCatalogo> findFirstByIdServicioAndIdProducto(Integer idServicio, Integer idProducto);

    /** Producto de verificacion (tipoFront 4) de un servicio, si GestoPago lo ofrece. */
    Optional<ProductoCatalogo> findFirstByIdServicioAndTipoFront(Integer idServicio, Integer tipoFront);
}
