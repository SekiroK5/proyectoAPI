package com.proyecto.servicios.repository.sf;

import com.proyecto.servicios.entity.sf.Cuenta;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    /**
     * SELECT ... FOR UPDATE: bloquea la fila de la cuenta hasta terminar la transaccion, para que dos
     * pagos simultaneos no lean el mismo saldo y lo dejen negativo.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Cuenta c where c.id = :id")
    Optional<Cuenta> bloquearPorId(@Param("id") Integer id);
}
