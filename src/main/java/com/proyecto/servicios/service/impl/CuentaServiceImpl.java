package com.proyecto.servicios.service.impl;

import com.proyecto.servicios.dto.request.CuentaBusquedaRequest;
import com.proyecto.servicios.dto.request.CuentaConsultaRequest;
import com.proyecto.servicios.dto.response.ApiResponse;
import com.proyecto.servicios.dto.response.CuentaResponse;
import com.proyecto.servicios.dto.response.PaginaResponse;
import com.proyecto.servicios.mapper.ClienteMapper;
import com.proyecto.servicios.repository.sf.CuentaRepository;
import com.proyecto.servicios.service.CuentaService;
import com.proyecto.servicios.specification.CuentaSpecification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class CuentaServiceImpl implements CuentaService {

    private static final Sort ORDEN = Sort.by("id");

    private final CuentaRepository cuentaRepository;

    public CuentaServiceImpl(CuentaRepository cuentaRepository) {
        this.cuentaRepository = cuentaRepository;
    }

    @Override
    @Transactional(value = "sfTransactionManager", readOnly = true)
    public ApiResponse<CuentaResponse> consultar(CuentaConsultaRequest request) {
        return cuentaRepository.findByNumeroCuenta(request.getNumeroCuenta())
                .map(c -> ApiResponse.exito(HttpStatus.OK, "Cuenta encontrada.", ClienteMapper.aResponse(c)))
                .orElseGet(() -> ApiResponse.error(HttpStatus.NOT_FOUND,
                        "No existe una cuenta con el número " + request.getNumeroCuenta() + "."));
    }

    @Override
    @Transactional(value = "sfTransactionManager", readOnly = true)
    public ApiResponse<PaginaResponse<CuentaResponse>> buscar(CuentaBusquedaRequest filtro) {
        PaginaResponse<CuentaResponse> pagina = PaginaResponse.of(
                cuentaRepository.findAll(CuentaSpecification.desde(filtro), filtro.toPageable(ORDEN)),
                ClienteMapper::aResponse);
        String mensaje = pagina.getTotalElementos() == 0
                ? "No se encontraron cuentas con los criterios proporcionados."
                : "Se encontraron " + pagina.getTotalElementos() + " cuenta(s).";
        return ApiResponse.exito(HttpStatus.OK, mensaje, pagina);
    }
}
