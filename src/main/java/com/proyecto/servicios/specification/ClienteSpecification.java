package com.proyecto.servicios.specification;

import com.proyecto.servicios.dto.request.ClienteBusquedaRequest;
import com.proyecto.servicios.entity.sf.Cliente;
import com.proyecto.servicios.entity.sf.Cuenta;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;

/**
 * Construye dinamicamente las condiciones de busqueda de clientes con la Criteria API de JPA.
 * Cada criterio devuelve un predicado nulo cuando su valor no se proporciona, por lo que queda
 * excluido de la consulta. Los valores siempre viajan como parametros (nunca se concatenan en SQL).
 */
public final class ClienteSpecification {

    private ClienteSpecification() {
    }

    /** Combina (AND) todos los criterios presentes en la solicitud. */
    public static Specification<Cliente> desde(ClienteBusquedaRequest filtro) {
        return Specification.allOf(
                nombreContiene(filtro.getNombre()),
                correoIgual(filtro.getCorreoElectronico()),
                telefonoIgual(filtro.getTelefono()),
                activo(filtro.getActivo()),
                curpIgual(filtro.getCurp()),
                rfcIgual(filtro.getRfc()),
                numeroCuentaIgual(filtro.getNumeroCuenta()),
                registradoDesde(filtro.getFechaRegistroDesde()),
                registradoHasta(filtro.getFechaRegistroHasta())
        );
    }

    /** Coincidencia parcial, sin distinguir mayusculas, en nombre(s) y apellidos. */
    public static Specification<Cliente> nombreContiene(String texto) {
        return (root, query, cb) -> {
            if (texto == null) {
                return null;
            }
            String patron = "%" + texto.toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("nombre")), patron),
                    cb.like(cb.lower(root.get("segundoNombre")), patron),
                    cb.like(cb.lower(root.get("apellidoPaterno")), patron),
                    cb.like(cb.lower(root.get("apellidoMaterno")), patron)
            );
        };
    }

    public static Specification<Cliente> correoIgual(String correo) {
        return (root, query, cb) -> correo == null ? null : cb.equal(root.get("correoElectronico"), correo);
    }

    /** Coincide con el telefono movil o con el alternativo. */
    public static Specification<Cliente> telefonoIgual(String telefono) {
        return (root, query, cb) -> telefono == null ? null : cb.or(
                cb.equal(root.get("telefonoMovil"), telefono),
                cb.equal(root.get("telefonoAlternativo"), telefono));
    }

    public static Specification<Cliente> activo(Boolean activo) {
        return (root, query, cb) -> activo == null ? null : cb.equal(root.get("activo"), activo);
    }

    public static Specification<Cliente> curpIgual(String curp) {
        return (root, query, cb) -> curp == null ? null : cb.equal(root.get("curp"), curp);
    }

    public static Specification<Cliente> rfcIgual(String rfc) {
        return (root, query, cb) -> rfc == null ? null : cb.equal(root.get("rfc"), rfc);
    }

    public static Specification<Cliente> numeroCuentaIgual(String numeroCuenta) {
        return (root, query, cb) -> {
            if (numeroCuenta == null) {
                return null;
            }
            Join<Cliente, Cuenta> cuenta = root.join("cuenta", JoinType.INNER);
            return cb.equal(cuenta.get("numeroCuenta"), numeroCuenta);
        };
    }

    public static Specification<Cliente> registradoDesde(LocalDate desde) {
        return (root, query, cb) -> desde == null ? null
                : cb.greaterThanOrEqualTo(root.get("fechaCreacion"), desde.atStartOfDay());
    }

    /** Incluye el dia completo indicado (limite superior exclusivo en el dia siguiente). */
    public static Specification<Cliente> registradoHasta(LocalDate hasta) {
        return (root, query, cb) -> hasta == null ? null
                : cb.lessThan(root.get("fechaCreacion"), hasta.plusDays(1).atStartOfDay());
    }
}
