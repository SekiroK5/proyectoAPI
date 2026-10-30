package com.proyecto.servicios.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Documentacion OpenAPI. Swagger UI: http://localhost:8081/swagger-ui/index.html
 * El boton "Authorize" recibe el token de acceso de POST /api/v1/auth/a2f/verificacion.
 */
@Configuration
public class OpenApi {

    private static final String ESQUEMA_JWT = "tokenAcceso";

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("API de Onboarding de Clientes")
                        .version("v1")
                        .description("Registro de clientes personas físicas con apertura automática de cuenta bancaria. "
                                + "Todos los datos se envían en el cuerpo de la solicitud (sin @PathVariable ni @RequestParam); "
                                + "los clientes se localizan con la llave compuesta nombre + correo electrónico. "
                                + "Salvo el alta, el inicio de sesión y el catálogo de productos, los endpoints requieren "
                                + "el token de acceso obtenido con contraseña + segundo factor (TOTP)."))
                .components(new Components().addSecuritySchemes(ESQUEMA_JWT, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(ESQUEMA_JWT));
    }
}
