package com.proyecto.servicios.specification;

import com.proyecto.servicios.dto.request.CuentaBusquedaRequest;
import com.proyecto.servicios.entity.sf.Cuenta;
import com.proyecto.servicios.enums.Catalogos.EstatusCuenta;
import org.springframework.data.jpa.domain.Specification;

/**
 * Condiciones dinamicas para la busqueda de cuentas.
 */
public final class CuentaSpecification {

    private CuentaSpecification() {
    }

    public static Specification<Cuenta> desde(CuentaBusquedaRequest filtro) {
        return Specification.allOf(
                estatusIgual(filtro.getEstatus()),
                curpTitular(filtro.getCurp())
        );
    }

    public static Specification<Cuenta> estatusIgual(EstatusCuenta estatus) {
        return (root, query, cb) -> estatus == null ? null : cb.equal(root.get("estatus"), estatus);
    }

    public static Specification<Cuenta> curpTitular(String curp) {
        return (root, query, cb) -> curp == null ? null : cb.equal(root.get("cliente").get("curp"), curp);
    }
}
