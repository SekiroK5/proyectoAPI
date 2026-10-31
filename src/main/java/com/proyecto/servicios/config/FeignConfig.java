package com.proyecto.servicios.config;

import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

/**
 * Clientes Feign de GestoPago/PuntoRed. Vive fuera de App para que las pruebas de la capa web
 * (@WebMvcTest) no intenten crear los clientes HTTP.
 */
@Configuration
@EnableFeignClients(basePackages = "com.proyecto.servicios.client")
public class FeignConfig {
}
