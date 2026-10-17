package com.webox.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Swagger UI metadata. Available at {@code /swagger-ui.html}. */
@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearerAuth";

    @Bean
    public OpenAPI weBoxOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("WeBox API")
                        .version("1.0.0")
                        .description("""
                                Enterprise employee meal ordering platform.

                                Employee endpoints require a Bearer token obtained from
                                `POST /api/auth/login`. Admin endpoints under `/api/admin/**`
                                additionally require the ADMIN role.

                                All user-facing messages returned by this API are in English.
                                """))
                .components(new Components().addSecuritySchemes(BEARER,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
