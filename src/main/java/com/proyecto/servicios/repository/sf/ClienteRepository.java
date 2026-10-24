package com.proyecto.servicios.repository.sf;

import com.proyecto.servicios.entity.sf.Cliente;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ClienteRepository extends JpaRepository<Cliente, Integer>, JpaSpecificationExecutor<Cliente> {

    boolean existsByCurp(String curp);

    boolean existsByRfc(String rfc);

    boolean existsByCorreoElectronico(String correoElectronico);

    boolean existsByCorreoElectronicoAndIdNot(String correoElectronico, Integer id);

    boolean existsByNombreUsuario(String nombreUsuario);

    /**
     * Llave compuesta: equivale a {@code WHERE lower(nombre) = lower(:nombre) AND correo_electronico = :correo}.
     * La consulta es parametrizada y el indice UNIQUE del correo la resuelve en un solo acceso.
     */
    @EntityGraph(attributePaths = {"domicilio", "cuenta"})
    Optional<Cliente> findByNombreIgnoreCaseAndCorreoElectronico(String nombre, String correoElectronico);

    /**
     * Busqueda dinamica paginada; carga domicilio y cuenta en la misma consulta para evitar N+1.
     */
    @Override
    @EntityGraph(attributePaths = {"domicilio", "cuenta"})
    Page<Cliente> findAll(Specification<Cliente> spec, Pageable pageable);
}
