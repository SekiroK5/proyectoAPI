package com.proyecto.servicios.repository.sf;

import com.proyecto.servicios.entity.sf.VerificacionReferencia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Repository
public interface VerificacionReferenciaRepository extends JpaRepository<VerificacionReferencia, String> {

    /** Verificaciones de un cliente desde cierta fecha: limita cuantas puede pedir por hora. */
    long countByClienteIdAndFechaCreacionAfter(Integer clienteId, LocalDateTime desde);

    /**
     * Marca el folio como usado de forma atomica. Solo una peticion lo logra (1); si otra ya lo uso, 0.
     */
    @Modifying
    @Transactional("sfTransactionManager")
    @Query("update VerificacionReferencia v set v.usada = true where v.folio = :folio and v.usada = false")
    int marcarUsada(@Param("folio") String folio);
}
