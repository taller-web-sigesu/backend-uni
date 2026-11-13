package pe.edu.universidad.servicios;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;

import pe.edu.universidad.servicios.support.Api.Response;
import pe.edu.universidad.servicios.support.IntegrationTest;
import tools.jackson.databind.JsonNode;

/**
 * Comprueba que la API publicada expone las 55 operaciones de negocio más /status del diseño
 * y exporta el contrato generado a target/openapi.yaml para compararlo con docs/api/openapi.yaml.
 */
class OpenApiContractIT extends IntegrationTest {

    private static final Set<String> EXPECTED = Set.of(
            "POST /api/v1/auth/login", "POST /api/v1/auth/refresh", "POST /api/v1/auth/logout", "PATCH /api/v1/auth/password",
            "POST /api/v1/auth/register", "GET /api/v1/admin/users/pending", "PATCH /api/v1/admin/users/{id}/account-status",
            "POST /api/v1/admin/users/{id}/password-reset", "POST /api/v1/requests/{id}/attachments",
            "GET /api/v1/requests/{id}/attachments", "GET /api/v1/requests/{id}/attachments/{attachmentId}/content",
            "DELETE /api/v1/requests/{id}/attachments/{attachmentId}",
            "POST /api/v1/requests", "GET /api/v1/requests", "GET /api/v1/requests/{id}", "GET /api/v1/requests/{id}/history",
            "GET /api/v1/requests/{id}/comments", "POST /api/v1/requests/{id}/comments",
            "GET /api/v1/requests/{id}/eligible-technicians", "POST /api/v1/requests/{id}/assignments",
            "PATCH /api/v1/requests/{id}", "PATCH /api/v1/requests/{id}/status",
            "GET /api/v1/dashboard/summary", "GET /api/v1/dashboard/reports",
            "GET /api/v1/admin/users", "GET /api/v1/admin/users/{id}", "POST /api/v1/admin/users", "PATCH /api/v1/admin/users/{id}",
            "GET /api/v1/admin/roles",
            "GET /api/v1/catalogs/categories",
            "GET /api/v1/admin/catalogs/areas", "POST /api/v1/admin/catalogs/areas", "PATCH /api/v1/admin/catalogs/areas/{id}",
            "GET /api/v1/admin/catalogs/categories", "POST /api/v1/admin/catalogs/categories",
            "PATCH /api/v1/admin/catalogs/categories/{id}",
            "GET /api/v1/admin/catalogs/priorities", "POST /api/v1/admin/catalogs/priorities",
            "PATCH /api/v1/admin/catalogs/priorities/{id}",
            "GET /api/v1/admin/catalogs/request-statuses", "PATCH /api/v1/admin/catalogs/request-statuses/{id}",
            "GET /api/v1/admin/sla-policies", "POST /api/v1/admin/sla-policies", "PATCH /api/v1/admin/sla-policies/{id}",
            "GET /api/v1/requests/{id}/audit-events", "POST /api/v1/requests/{id}/survey",
            "GET /api/v1/spaces", "GET /api/v1/spaces/{id}/availability", "POST /api/v1/reservations", "GET /api/v1/reservations",
            "GET /api/v1/reservations/{id}", "GET /api/v1/reservations/{id}/history", "PATCH /api/v1/reservations/{id}/status",
            "GET /api/v1/admin/spaces", "POST /api/v1/admin/spaces", "PATCH /api/v1/admin/spaces/{id}",
            "GET /api/v1/admin/space-types", "POST /api/v1/admin/space-types", "PATCH /api/v1/admin/space-types/{id}",
            "GET /api/v1/admin/spaces/{id}/schedule", "PUT /api/v1/admin/spaces/{id}/schedule",
            "GET /api/v1/admin/reservation-policy", "PUT /api/v1/admin/reservation-policy",
            "GET /api/v1/status");

    @Test
    void contratoPublicadoCubreLasOperacionesDelDiseno() throws IOException {
        Response docs = api.get("/v3/api-docs", null);
        assertThat(docs.status()).isEqualTo(200);
        JsonNode paths = docs.json().get("paths");
        Set<String> published = new TreeSet<>();
        for (String path : paths.propertyNames()) {
            for (String method : paths.get(path).propertyNames()) {
                published.add(method.toUpperCase() + " " + path);
            }
        }
        assertThat(EXPECTED).hasSize(64);
        List<String> missing = new ArrayList<>(EXPECTED);
        missing.removeAll(published);
        assertThat(missing).as("operaciones sin publicar").isEmpty();
        assertThat(published).containsExactlyInAnyOrderElementsOf(EXPECTED);

        Response yaml = api.get("/v3/api-docs.yaml", null);
        assertThat(yaml.status()).isEqualTo(200);
        Path target = Path.of("target", "openapi.yaml");
        Files.writeString(target, yaml.body(), StandardCharsets.UTF_8);
    }
}
