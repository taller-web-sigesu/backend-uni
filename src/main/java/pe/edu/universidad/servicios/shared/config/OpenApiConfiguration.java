package pe.edu.universidad.servicios.shared.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;

/**
 * Metadatos del contrato publicado en /v3/api-docs (exportado a docs/api/openapi.yaml).
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfiguration {

    @Bean
    OpenAPI gestalOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("API de Servicios Universitarios (Gestal)")
                        .version("v1")
                        .description("""
                                Contrato REST de HU-01 a HU-25. Identificadores como cadenas decimales, instantes en UTC,
                                fechas y horas de reserva en America/Lima, paginación desde 0 (20 por defecto, 100 como máximo)
                                y errores application/problem+json con codigo y correlationId."""))
                .servers(List.of(new Server().url("/").description("Origen actual")))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
