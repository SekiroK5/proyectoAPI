package com.proyecto.servicios.repository.sf;

import com.proyecto.servicios.entity.sf.Cliente;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
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
     * Inicio de sesion: el mismo valor se compara contra el usuario y contra el correo (ambos en minusculas
     * y UNIQUE). Carga la cuenta para devolver su numero al iniciar sesion.
     */
    @EntityGraph(attributePaths = {"cuenta"})
    Optional<Cliente> findByNombreUsuarioOrCorreoElectronico(String nombreUsuario, String correoElectronico);

    @EntityGraph(attributePaths = {"cuenta"})
    Optional<Cliente> findConCuentaById(Integer id);

    /**
     * Marca como usado el paso TOTP de forma atomica: solo actualiza si es posterior al ultimo usado.
     * Si dos peticiones llegan al mismo tiempo con el mismo codigo, solo una obtiene 1; la otra obtiene 0.
     */
    @Modifying
    @Transactional("sfTransactionManager")
    @Query("update Cliente c set c.a2fUltimoPaso = :paso "
            + "where c.id = :id and (c.a2fUltimoPaso is null or c.a2fUltimoPaso < :paso)")
    int marcarPasoA2F(@Param("id") Integer id, @Param("paso") long paso);

    /**
     * Busqueda dinamica paginada; carga domicilio y cuenta en la misma consulta para evitar N+1.
     */
    @Override
    @EntityGraph(attributePaths = {"domicilio", "cuenta"})
    Page<Cliente> findAll(Specification<Cliente> spec, Pageable pageable);
}
