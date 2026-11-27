package com.proyecto.servicios.service.impl;

import com.proyecto.servicios.client.RecursosGestoPago;
import com.proyecto.servicios.dto.request.DetalleTransaccionRequest;
import com.proyecto.servicios.dto.request.HistorialTransaccionesRequest;
import com.proyecto.servicios.dto.response.ApiResponse;
import com.proyecto.servicios.dto.response.PaginaResponse;
import com.proyecto.servicios.dto.response.TransaccionResponse;
import com.proyecto.servicios.entity.sf.ProductoCatalogo;
import com.proyecto.servicios.entity.sf.Transaccion;
import com.proyecto.servicios.mapper.TransaccionMapper;
import com.proyecto.servicios.repository.sf.ProductoCatalogoRepository;
import com.proyecto.servicios.repository.sf.TransaccionRepository;
import com.proyecto.servicios.service.TransaccionService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
public class TransaccionServiceImpl implements TransaccionService {

    static final String MSG_NO_ENCONTRADA = "No existe una transacción con ese id en sus operaciones.";
    private static final Sort MAS_RECIENTE_PRIMERO = Sort.by(Sort.Direction.DESC, "fechaCreacion").and(Sort.by(Sort.Direction.DESC, "id"));

    private final TransaccionRepository transaccionRepository;
    private final ProductoCatalogoRepository catalogoRepository;
    private final RecursosGestoPago recursos;

    public TransaccionServiceImpl(TransaccionRepository transaccionRepository,
                                  ProductoCatalogoRepository catalogoRepository,
                                  RecursosGestoPago recursos) {
        this.transaccionRepository = transaccionRepository;
        this.catalogoRepository = catalogoRepository;
        this.recursos = recursos;
    }

    @Override
    @Transactional(value = "sfTransactionManager", readOnly = true)
    public ApiResponse<PaginaResponse<TransaccionResponse>> historial(Integer clienteId, HistorialTransaccionesRequest request) {
        Page<Transaccion> pagina = request.getEstatus() == null
                ? transaccionRepository.findByCuenta_Cliente_Id(clienteId, request.toPageable(MAS_RECIENTE_PRIMERO))
                : transaccionRepository.findByCuenta_Cliente_IdAndEstatus(clienteId, request.getEstatus(),
                        request.toPageable(MAS_RECIENTE_PRIMERO));
        Map<String, ProductoCatalogo> catalogo = catalogoPorLlave();
        PaginaResponse<TransaccionResponse> datos = PaginaResponse.of(pagina, tx -> conServicio(tx, catalogo));
        String mensaje = datos.getTotalElementos() == 0
                ? "Aún no tiene pagos registrados."
                : "Se encontraron " + datos.getTotalElementos() + " pago(s).";
        return ApiResponse.exito(HttpStatus.OK, mensaje, datos);
    }

    @Override
    @Transactional(value = "sfTransactionManager", readOnly = true)
    public ApiResponse<TransaccionResponse> detalle(Integer clienteId, DetalleTransaccionRequest request) {
        return transaccionRepository.findByIdAndCuenta_Cliente_Id(request.getId(), clienteId)
                .map(tx -> ApiResponse.exito(HttpStatus.OK, "Transacción encontrada.", conServicio(tx, catalogoPorLlave())))
                .orElseGet(() -> ApiResponse.error(HttpStatus.NOT_FOUND, MSG_NO_ENCONTRADA));
    }

    private TransaccionResponse conServicio(Transaccion tx, Map<String, ProductoCatalogo> catalogo) {
        ProductoCatalogo producto = catalogo.get(llave(tx.getIdServicio(), tx.getIdProducto()));
        return TransaccionMapper.conServicio(TransaccionMapper.aResponse(tx), producto, recursos);
    }

    /** El catalogo es chico (cientos de productos): se carga una vez por consulta en lugar de uno por fila. */
    private Map<String, ProductoCatalogo> catalogoPorLlave() {
        Map<String, ProductoCatalogo> mapa = new HashMap<>();
        for (ProductoCatalogo p : catalogoRepository.findAll()) {
            mapa.putIfAbsent(llave(p.getIdServicio(), p.getIdProducto()), p);
        }
        return mapa;
    }

    private static String llave(Integer idServicio, Integer idProducto) {
        return idServicio + ":" + idProducto;
    }
}
