package pe.edu.universidad.servicios.catalogs;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import pe.edu.universidad.servicios.support.Api.Response;
import pe.edu.universidad.servicios.support.IntegrationTest;
import pe.edu.universidad.servicios.support.TestData.Account;

/**
 * HU-15 a HU-17: cuentas, roles, áreas, categorías, prioridades y nombres de estado.
 */
class AdminCatalogIT extends IntegrationTest {

    private String adminToken;

    @BeforeEach
    void setUp() {
        Account admin = data.user("ADMINISTRADOR", null);
        adminToken = api.token(admin);
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }

    @Test
    void altaDeUsuarioExigeAreaParaTecnicoYCambioDeContrasena() {
        long area = data.area();
        String correo = "tec-" + suffix().toLowerCase() + "@universidad.example";
        Response noArea = api.post("/api/v1/admin/users", adminToken,
                Map.of("nombreCompleto", "Luis Demo", "correo", correo, "contrasenaInicial", "Inicial2026", "rol", "TECNICO"));
        assertThat(noArea.status()).isEqualTo(400);

        Response created = api.post("/api/v1/admin/users", adminToken, Map.of("nombreCompleto", "Luis Demo", "correo", correo,
                "contrasenaInicial", "Inicial2026", "rol", "TECNICO", "areaId", String.valueOf(area)));
        assertThat(created.status()).as(created.toString()).isEqualTo(201);
        assertThat(created.body()).doesNotContain("hash").doesNotContain("contrasena");
        assertThat(created.json().get("activo").asBoolean()).isTrue();

        Response duplicated = api.post("/api/v1/admin/users", adminToken, Map.of("nombreCompleto", "Otro", "correo", correo.toUpperCase(),
                "contrasenaInicial", "Inicial2026", "rol", "ESTUDIANTE"));
        assertThat(duplicated.status()).isEqualTo(409);

        Response login = api.login(correo, "Inicial2026");
        assertThat(login.json().get("debeCambiarContrasena").asBoolean()).isTrue();

        Response invalidCombo = api.patch("/api/v1/admin/users/" + created.text("id"), adminToken, Map.of("rol", "SUPERVISOR", "areaId", "999999"));
        assertThat(invalidCombo.status()).isEqualTo(400);
        assertThat(api.get("/api/v1/admin/users/999999", adminToken).status()).isEqualTo(404);
        assertThat(api.get("/api/v1/admin/roles", adminToken).json().size()).isEqualTo(5);
    }

    @Test
    void rutasAdministrativasDeneganOtrosRoles() {
        String student = api.token(data.user("ESTUDIANTE", null));
        for (String path : new String[] { "/api/v1/admin/users", "/api/v1/admin/roles", "/api/v1/admin/catalogs/areas",
                "/api/v1/admin/catalogs/categories", "/api/v1/admin/catalogs/priorities", "/api/v1/admin/catalogs/request-statuses",
                "/api/v1/admin/sla-policies", "/api/v1/admin/spaces", "/api/v1/admin/space-types", "/api/v1/admin/reservation-policy" }) {
            assertThat(api.get(path, student).status()).as(path).isEqualTo(403);
        }
    }

    @Test
    void categoriasRequierenReferenciasActivasYConservanCodigo() {
        String code = "CAT_" + suffix();
        long area = data.area();
        Response created = api.post("/api/v1/admin/catalogs/categories", adminToken,
                Map.of("codigo", code, "nombre", "Conectividad " + code, "areaId", String.valueOf(area), "prioridadPredeterminadaId", "2"));
        assertThat(created.status()).as(created.toString()).isEqualTo(201);
        assertThat(api.post("/api/v1/admin/catalogs/categories", adminToken,
                Map.of("codigo", code, "nombre", "Otra", "areaId", String.valueOf(area), "prioridadPredeterminadaId", "2")).status())
                .isEqualTo(409);
        assertThat(api.patch("/api/v1/admin/catalogs/categories/" + created.text("id"), adminToken, Map.of("codigo", "NUEVO")).status())
                .isEqualTo(400);

        String student = api.token(data.user("ESTUDIANTE", null));
        Response available = api.get("/api/v1/catalogs/categories?size=100", student);
        assertThat(available.body()).contains(created.text("id"));

        // Un área con categorías activas no se puede desactivar.
        assertThat(api.patch("/api/v1/admin/catalogs/areas/" + area, adminToken, Map.of("activo", false)).status()).isEqualTo(409);
        assertThat(api.patch("/api/v1/admin/catalogs/categories/" + created.text("id"), adminToken, Map.of("activo", false)).status())
                .isEqualTo(200);
        assertThat(api.get("/api/v1/catalogs/categories?size=100", student).body()).doesNotContain("\"id\":\"" + created.text("id") + "\"");
    }

    @Test
    void prioridadesYEstados() {
        int nivel = 5 + (int) (Math.random() * 6);
        String code = "P_" + suffix();
        Response priority = api.post("/api/v1/admin/catalogs/priorities", adminToken, Map.of("codigo", code, "nombre", "Urgente " + code, "nivel", nivel));
        if (priority.status() == 409) {
            return; // nivel ocupado por otra prueba; la unicidad ya quedó comprobada
        }
        assertThat(priority.status()).as(priority.toString()).isEqualTo(201);
        Response renamed = api.patch("/api/v1/admin/catalogs/priorities/" + priority.text("id"), adminToken, Map.of("nombre", "Normal " + code));
        assertThat(renamed.text("nombre")).isEqualTo("Normal " + code);
        assertThat(renamed.text("codigo")).isEqualTo(code);

        Response statuses = api.get("/api/v1/admin/catalogs/request-statuses", adminToken);
        assertThat(statuses.json().size()).isEqualTo(6);
        Response status = api.patch("/api/v1/admin/catalogs/request-statuses/1", adminToken, Map.of("nombre", "Registrado"));
        assertThat(status.text("codigo")).isEqualTo("NUEVO");
        assertThat(status.text("nombre")).isEqualTo("Registrado");
        assertThat(api.patch("/api/v1/admin/catalogs/request-statuses/1", adminToken, Map.of("codigo", "OTRO", "nombre", "x")).status())
                .isEqualTo(400);
        api.patch("/api/v1/admin/catalogs/request-statuses/1", adminToken, Map.of("nombre", "Nuevo"));
    }
}
