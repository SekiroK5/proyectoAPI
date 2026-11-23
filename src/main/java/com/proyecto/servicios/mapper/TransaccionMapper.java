package com.proyecto.servicios.mapper;

import com.proyecto.servicios.client.RecursosGestoPago;
import com.proyecto.servicios.dto.response.TransaccionResponse;
import com.proyecto.servicios.entity.sf.ProductoCatalogo;
import com.proyecto.servicios.entity.sf.Transaccion;
import com.proyecto.servicios.enums.Catalogos.EstatusTransaccion;

/**
 * Transaccion (entidad) a TransaccionResponse, para el resultado del pago, el historial y el detalle.
 */
public final class TransaccionMapper {

    private TransaccionMapper() {
    }

    public static TransaccionResponse aResponse(Transaccion tx) {
        TransaccionResponse res = new TransaccionResponse();
        res.setId(tx.getId());
        res.setUpc(tx.getUpc());
        res.setEstatus(tx.getEstatus());
        res.setNumeroCuenta(tx.getCuenta().getNumeroCuenta());
        res.setSaldoCuenta(tx.getCuenta().getSaldo());
        res.setIdServicio(tx.getIdServicio());
        res.setIdProducto(tx.getIdProducto());
        res.setTelefono(tx.getTelefono());
        res.setReferencia(tx.getReferencia());
        res.setMonto(tx.getMonto());
        res.setNumAutorizacion(tx.getNumAutorizacion());
        res.setIdTx(tx.getIdTx());
        res.setPin(tx.getPin());
        res.setCodigoGestoPago(tx.getCodigoRespuesta());
        // Solo el texto de exito; un error interno (timeout, red) no se expone al cliente
        if (tx.getEstatus() == EstatusTransaccion.APLICADA) {
            res.setMensajeGestoPago(tx.getTextoRespuesta());
        }
        res.setFecha(tx.getFechaCreacion());
        return res;
    }

    /** Agrega nombre, categoria y logotipo del servicio a partir del catalogo. */
    public static TransaccionResponse conServicio(TransaccionResponse res, ProductoCatalogo producto, RecursosGestoPago recursos) {
        res.setLogoUrl(recursos.logoUrl(res.getIdServicio()));
        if (producto != null) {
            res.setServicio(producto.getServicio());
            res.setProducto(producto.getProducto());
            res.setCategoria(recursos.categoria(producto.getIdCatTipoServicio()));
        }
        return res;
    }
}
