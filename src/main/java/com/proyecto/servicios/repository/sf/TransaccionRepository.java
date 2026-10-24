package com.proyecto.servicios.repository.sf;

import com.proyecto.servicios.entity.sf.Transaccion;
import com.proyecto.servicios.enums.Catalogos.EstatusTransaccion;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransaccionRepository extends JpaRepository<Transaccion, Integer> {

    boolean existsByUpc(String upc);

    /** Evita que el handler y el proceso de confirmTx cierren la misma transaccion al mismo tiempo. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Transaccion t join fetch t.cuenta where t.id = :id")
    Optional<Transaccion> bloquearPorId(@Param("id") Integer id);

    /** Transacciones sin resultado definitivo creadas dentro de la ventana permitida para confirmTx. */
    List<Transaccion> findByEstatusInAndFechaCreacionBetweenOrderByFechaCreacion(
            Collection<EstatusTransaccion> estatus, LocalDateTime desde, LocalDateTime hasta);
}
