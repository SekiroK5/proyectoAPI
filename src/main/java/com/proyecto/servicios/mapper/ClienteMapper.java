package com.proyecto.servicios.mapper;

import com.proyecto.servicios.dto.request.ClienteCreateRequest;
import com.proyecto.servicios.dto.request.ClienteUpdateRequest;
import com.proyecto.servicios.dto.request.DomicilioRequest;
import com.proyecto.servicios.dto.request.DomicilioUpdateRequest;
import com.proyecto.servicios.dto.response.ClienteResponse;
import com.proyecto.servicios.dto.response.CuentaResponse;
import com.proyecto.servicios.dto.response.DomicilioResponse;
import com.proyecto.servicios.entity.sf.Cliente;
import com.proyecto.servicios.entity.sf.Cuenta;
import com.proyecto.servicios.entity.sf.Domicilio;
import com.proyecto.servicios.util.Texto;

import java.math.RoundingMode;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Conversion entre DTOs y entidades. Las entidades nunca salen de la capa de servicio.
 */
public final class ClienteMapper {

    private ClienteMapper() {
    }

    // ======================== Alta ========================

    /** Crea la entidad con los datos capturados; los campos controlados por el servidor no se copian. */
    public static Cliente aEntidad(ClienteCreateRequest req) {
        Cliente c = new Cliente();
        c.setNombre(req.getNombre().trim());
        c.setSegundoNombre(req.getSegundoNombre());
        c.setApellidoPaterno(req.getApellidoPaterno().trim());
        c.setApellidoMaterno(req.getApellidoMaterno().trim());
        c.setFechaNacimiento(req.getFechaNacimiento());
        c.setCurp(req.getCurp());
        c.setRfc(req.getRfc());
        c.setSexo(req.getSexo());
        c.setNacionalidad(req.getNacionalidad().trim());
        c.setEstadoCivil(req.getEstadoCivil());
        c.setCorreoElectronico(req.getCorreoElectronico());
        c.setNombreUsuario(req.getNombreUsuario());
        c.setTelefonoMovil(req.getTelefonoMovil());
        c.setTelefonoAlternativo(req.getTelefonoAlternativo());
        c.setOcupacion(req.getOcupacion().trim());
        c.setEmpresa(req.getEmpresa().trim());
        c.setIngresoMensual(req.getIngresoMensual().setScale(2, RoundingMode.HALF_UP));
        c.setDomicilio(aEntidad(req.getDomicilio()));
        return c;
    }

    private static Domicilio aEntidad(DomicilioRequest req) {
        Domicilio d = new Domicilio();
        d.setCalle(req.getCalle().trim());
        d.setNumeroExterior(req.getNumeroExterior().trim());
        d.setNumeroInterior(req.getNumeroInterior());
        d.setColonia(req.getColonia().trim());
        d.setMunicipio(req.getMunicipio().trim());
        d.setEstado(req.getEstado().trim());
        d.setCodigoPostal(req.getCodigoPostal());
        d.setPais(req.getPais().trim());
        return d;
    }

    // ======================== Actualizacion parcial ========================

    /**
     * Aplica solo los campos enviados. Un valor null (u omitido) en un campo obligatorio no modifica nada;
     * en los campos opcionales {@code Optional.empty()} (null explicito) borra el valor.
     * El estado {@code activo} lo gestiona el servicio porque afecta tambien a la cuenta.
     */
    public static void aplicarCambios(ClienteUpdateRequest req, Cliente c) {
        siViene(req.getNuevoNombre(), c::setNombre);
        siViene(req.getNuevoCorreoElectronico(), c::setCorreoElectronico);
        siVieneOpcional(req.getSegundoNombre(), c::setSegundoNombre);
        siViene(Texto.limpiar(req.getApellidoPaterno()), c::setApellidoPaterno);
        siViene(Texto.limpiar(req.getApellidoMaterno()), c::setApellidoMaterno);
        siViene(req.getFechaNacimiento(), c::setFechaNacimiento);
        siViene(req.getSexo(), c::setSexo);
        siViene(Texto.limpiar(req.getNacionalidad()), c::setNacionalidad);
        siViene(req.getEstadoCivil(), c::setEstadoCivil);
        siViene(req.getTelefonoMovil(), c::setTelefonoMovil);
        siVieneOpcional(req.getTelefonoAlternativo(), c::setTelefonoAlternativo);
        siViene(Texto.limpiar(req.getOcupacion()), c::setOcupacion);
        siViene(Texto.limpiar(req.getEmpresa()), c::setEmpresa);
        siViene(req.getIngresoMensual(), v -> c.setIngresoMensual(v.setScale(2, RoundingMode.HALF_UP)));

        DomicilioUpdateRequest dom = req.getDomicilio();
        if (dom != null) {
            Domicilio d = c.getDomicilio();
            siViene(Texto.limpiar(dom.getCalle()), d::setCalle);
            siViene(Texto.limpiar(dom.getNumeroExterior()), d::setNumeroExterior);
            siVieneOpcional(dom.getNumeroInterior(), d::setNumeroInterior);
            siViene(Texto.limpiar(dom.getColonia()), d::setColonia);
            siViene(Texto.limpiar(dom.getMunicipio()), d::setMunicipio);
            siViene(Texto.limpiar(dom.getEstado()), d::setEstado);
            siViene(dom.getCodigoPostal(), d::setCodigoPostal);
            siViene(Texto.limpiar(dom.getPais()), d::setPais);
        }
    }

    private static <T> void siViene(T valor, Consumer<T> asignar) {
        if (valor != null) {
            asignar.accept(valor);
        }
    }

    /** null = campo omitido (sin cambio); Optional.empty() = null explicito (se borra). */
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private static void siVieneOpcional(Optional<String> valor, Consumer<String> asignar) {
        if (valor != null) {
            asignar.accept(valor.map(Texto::limpiar).orElse(null));
        }
    }

    // ======================== Respuestas ========================

    public static ClienteResponse aResponse(Cliente cliente) {
        ClienteResponse res = new ClienteResponse();
        res.setId(cliente.getId());
        res.setNombre(cliente.getNombre());
        res.setSegundoNombre(cliente.getSegundoNombre());
        res.setApellidoPaterno(cliente.getApellidoPaterno());
        res.setApellidoMaterno(cliente.getApellidoMaterno());
        res.setFechaNacimiento(cliente.getFechaNacimiento());
        res.setCurp(cliente.getCurp());
        res.setRfc(cliente.getRfc());
        res.setSexo(cliente.getSexo());
        res.setNacionalidad(cliente.getNacionalidad());
        res.setEstadoCivil(cliente.getEstadoCivil());
        res.setCorreoElectronico(cliente.getCorreoElectronico());
        res.setNombreUsuario(cliente.getNombreUsuario());
        res.setTelefonoMovil(cliente.getTelefonoMovil());
        res.setTelefonoAlternativo(cliente.getTelefonoAlternativo());
        res.setOcupacion(cliente.getOcupacion());
        res.setEmpresa(cliente.getEmpresa());
        res.setIngresoMensual(cliente.getIngresoMensual());
        res.setActivo(cliente.getActivo());
        res.setFechaCreacion(cliente.getFechaCreacion());
        res.setFechaActualizacion(cliente.getFechaActualizacion());

        Domicilio dom = cliente.getDomicilio();
        if (dom != null) {
            DomicilioResponse domRes = new DomicilioResponse();
            domRes.setCalle(dom.getCalle());
            domRes.setNumeroExterior(dom.getNumeroExterior());
            domRes.setNumeroInterior(dom.getNumeroInterior());
            domRes.setColonia(dom.getColonia());
            domRes.setMunicipio(dom.getMunicipio());
            domRes.setEstado(dom.getEstado());
            domRes.setCodigoPostal(dom.getCodigoPostal());
            domRes.setPais(dom.getPais());
            res.setDomicilio(domRes);
        }

        Cuenta cuenta = cliente.getCuenta();
        if (cuenta != null) {
            res.setNumeroCuenta(cuenta.getNumeroCuenta());
            res.setSaldoCuenta(cuenta.getSaldo());
            res.setEstatusCuenta(cuenta.getEstatus());
        }
        return res;
    }

    public static CuentaResponse aResponse(Cuenta cuenta) {
        CuentaResponse res = new CuentaResponse();
        res.setNumeroCuenta(cuenta.getNumeroCuenta());
        res.setSaldo(cuenta.getSaldo());
        res.setEstatus(cuenta.getEstatus());
        res.setFechaApertura(cuenta.getFechaApertura());
        Cliente cliente = cuenta.getCliente();
        if (cliente != null) {
            res.setNombreCliente(nombreCompleto(cliente));
            res.setCorreoCliente(cliente.getCorreoElectronico());
            res.setClienteActivo(cliente.getActivo());
        }
        return res;
    }

    private static String nombreCompleto(Cliente c) {
        StringBuilder sb = new StringBuilder(c.getNombre());
        if (c.getSegundoNombre() != null) {
            sb.append(' ').append(c.getSegundoNombre());
        }
        return sb.append(' ').append(c.getApellidoPaterno())
                .append(' ').append(c.getApellidoMaterno())
                .toString();
    }
}
