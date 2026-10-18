package com.cubeworks.hr.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI / Swagger UI configuration stub.
 *
 * <p>Exposes the interactive API documentation at {@code /swagger-ui.html} and the
 * machine-readable spec at {@code /v3/api-docs}. The security scheme is pre-configured
 * so that the Swagger UI can send a Keycloak Bearer token for authenticated requests.</p>
 *
 * <p>This is a scaffold stub — actual endpoint documentation is populated by
 * {@code @RestController} classes added in later gates.</p>
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    /**
     * Configures the top-level OpenAPI metadata and global Bearer token security scheme.
     *
     * @return the {@link OpenAPI} instance used by SpringDoc to generate the spec
     */
    @Bean
    public OpenAPI hrBackendOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("CubeWorks HR API")
                        .description(
                                "REST API for the CubeWorks HR employee-lifecycle application. "
                                + "Authenticate with a Keycloak-issued Bearer token.")
                        .version("v1"))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
