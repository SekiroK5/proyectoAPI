package com.proyecto.servicios.repository.sf;

import com.proyecto.servicios.entity.sf.TokenRevocado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Repository
public interface TokenRevocadoRepository extends JpaRepository<TokenRevocado, String> {

    /** Los tokens ya vencidos no necesitan seguir en la lista: el decoder los rechaza por expiracion. */
    @Modifying
    @Transactional("sfTransactionManager")
    @Query("delete from TokenRevocado t where t.expira < :ahora")
    int borrarVencidos(@Param("ahora") LocalDateTime ahora);
}
