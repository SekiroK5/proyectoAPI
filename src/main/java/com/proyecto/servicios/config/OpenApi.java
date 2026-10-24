package com.proyecto.servicios.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Documentacion OpenAPI. Swagger UI: http://localhost:8081/swagger-ui/index.html
 */
@Configuration
public class OpenApi {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("API de Onboarding de Clientes")
                        .version("v1")
                        .description("Registro de clientes personas físicas con apertura automática de cuenta bancaria. "
                                + "Todos los datos se envían en el cuerpo de la solicitud (sin @PathVariable ni @RequestParam); "
                                + "los clientes se localizan con la llave compuesta nombre + correo electrónico."))
                .addServersItem(new Server().url("http://localhost:8081"));
    }
}
