package com.proyecto.servicios.repository.sf;

import com.proyecto.servicios.entity.sf.Transaccion;
import com.proyecto.servicios.enums.Catalogos.EstatusTransaccion;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
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

    /** Historial del cliente: solo sus transacciones (a traves de su cuenta). */
    @EntityGraph(attributePaths = "cuenta")
    Page<Transaccion> findByCuenta_Cliente_Id(Integer clienteId, Pageable pageable);

    @EntityGraph(attributePaths = "cuenta")
    Page<Transaccion> findByCuenta_Cliente_IdAndEstatus(Integer clienteId, EstatusTransaccion estatus, Pageable pageable);

    /** Detalle: la transaccion solo se encuentra si es del cliente. */
    @EntityGraph(attributePaths = "cuenta")
    Optional<Transaccion> findByIdAndCuenta_Cliente_Id(Integer id, Integer clienteId);

    /** Evita que el handler y el proceso de confirmTx cierren la misma transaccion al mismo tiempo. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Transaccion t join fetch t.cuenta where t.id = :id")
    Optional<Transaccion> bloquearPorId(@Param("id") Integer id);

    /**
     * Transacciones sin resultado definitivo, creadas dentro de la ventana permitida para confirmTx
     * y cuyo siguiente reintento ya toca.
     */
    @Query("select t from Transaccion t where t.estatus in :estatus and t.fechaCreacion between :desde and :hasta "
            + "and (t.proximaConfirmacion is null or t.proximaConfirmacion <= :ahora) order by t.fechaCreacion")
    List<Transaccion> buscarPorConfirmar(@Param("estatus") Collection<EstatusTransaccion> estatus,
                                         @Param("desde") LocalDateTime desde,
                                         @Param("hasta") LocalDateTime hasta,
                                         @Param("ahora") LocalDateTime ahora);
}
