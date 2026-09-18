package com.proyecto.servicios.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "productoClient", url = "${productos.service.url}")
public interface ProductoClient {

    /**
     * Obtiene la lista de servicios y productos que el distribuidor tiene autorizados para vender.
     * IMPORTANTE: Este metodo debe llamarse maximo 3 veces al dia segun la documentacion de PuntoRed.
     *
     * @param authorization Header de autorizacion con formato "Bearer {token}"
     * @return Respuesta XML con la lista de productos
     */
    @GetMapping("/sistema/service/getProductList.do")
    String getProductList(@RequestHeader("Authorization") String authorization);
}
