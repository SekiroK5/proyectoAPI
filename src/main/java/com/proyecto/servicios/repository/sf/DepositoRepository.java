package com.proyecto.servicios.repository.sf;

import com.proyecto.servicios.entity.sf.Deposito;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DepositoRepository extends JpaRepository<Deposito, Integer> {
}
