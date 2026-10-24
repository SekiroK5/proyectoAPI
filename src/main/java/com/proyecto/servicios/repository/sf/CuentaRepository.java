package com.proyecto.servicios.repository.sf;

import com.proyecto.servicios.entity.sf.Cuenta;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CuentaRepository extends JpaRepository<Cuenta, Integer>, JpaSpecificationExecutor<Cuenta> {

    boolean existsByNumeroCuenta(String numeroCuenta);

    @EntityGraph(attributePaths = "cliente")
    Optional<Cuenta> findByNumeroCuenta(String numeroCuenta);

    @Override
    @EntityGraph(attributePaths = "cliente")
    Page<Cuenta> findAll(Specification<Cuenta> spec, Pageable pageable);
}
