package pe.edu.universidad.servicios.users;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import pe.edu.universidad.servicios.support.Api.Response;
import pe.edu.universidad.servicios.support.IntegrationTest;
import pe.edu.universidad.servicios.support.TestData.Account;
import tools.jackson.databind.JsonNode;

/**
 * HU-28 (aprobar o rechazar cuentas auto-registradas) y HU-29 (restablecer la contraseña de un usuario).
 */
class AccountAdminIT extends IntegrationTest {

    private static final String PASSWORD = "Registro2026x";

    private Account admin;
    private String adminToken;

    @BeforeEach
    void setUp() {
        admin = data.user("ADMINISTRADOR", null);
        adminToken = api.token(admin);
    }

    private String registerPending() {
        String correo = "pend." + java.util.UUID.randomUUID().toString().substring(0, 8) + "@unmsm.edu.pe";
        Response r = api.post("/api/v1/auth/register", null, Map.of("nombreCompleto", "Pendiente Demo", "correo", correo,
                "contrasena", PASSWORD, "confirmacionContrasena", PASSWORD));
        assertThat(r.status()).as(r.toString()).isEqualTo(201);
        return correo;
    }

    private long idOf(String correo) {
        return data.jdbc().sql("SELECT id FROM usuarios WHERE correo = :c").param("c", correo).query(Long.class).single();
    }

    private Map<String, Object> row(long id) {
        return data.jdbc().sql("SELECT estado_cuenta, revisado_por_id, revisado_en, motivo_rechazo FROM usuarios WHERE id = :id")
                .param("id", id).query().singleRow();
    }

    private long audits(long id, String accion) {
        return data.jdbc().sql("SELECT COUNT(*) FROM auditoria_eventos WHERE entidad = 'usuarios' AND entidad_id = :id AND accion = :a")
                .param("id", id).param("a", accion).query(Long.class).single();
    }

    private Response decide(long id, String token, Map<String, Object> body) {
        return api.patch("/api/v1/admin/users/" + id + "/account-status", token, body);
    }

    private List<String> pendingIds() {
        Response r = api.get("/api/v1/admin/users/pending?size=100", adminToken);
        assertThat(r.status()).as(r.toString()).isEqualTo(200);
        List<String> ids = new ArrayList<>();
        for (JsonNode item : r.json().get("items")) {
            ids.add(item.get("id").asString());
        }
        return ids;
    }

    @Test
    void bandejaPendientesOrdenadaMasAntiguaPrimero() {
        // Las cuentas pendientes de otras pruebas se aprueban para que la bandeja solo tenga las de esta prueba.
        data.jdbc().sql("UPDATE usuarios SET estado_cuenta = 'ACTIVA' WHERE estado_cuenta = 'PENDIENTE'").update();
        List<String> expected = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            expected.add(String.valueOf(idOf(registerPending())));
            clock.advance(Duration.ofMinutes(1));
        }

        Response r = api.get("/api/v1/admin/users/pending", adminToken);
        assertThat(r.json().get("totalElements").asLong()).isEqualTo(3);
        assertThat(pendingIds()).containsExactlyElementsOf(expected);
        JsonNode first = r.json().get("items").get(0);
        assertThat(first.get("nombreCompleto").asString()).isEqualTo("Pendiente Demo");
        assertThat(first.get("correo").asString()).endsWith("@unmsm.edu.pe");
        assertThat(first.get("creadoEn").asString()).isNotBlank();
        assertThat(first.get("estadoCuenta").asString()).isEqualTo("PENDIENTE");
        assertThat(pendingIds()).doesNotContain(String.valueOf(admin.id()));
    }

    @Test
    void aprobarPermiteLogin() {
        String correo = registerPending();
        long id = idOf(correo);

        Response approved = decide(id, adminToken, Map.of("estadoCuenta", "ACTIVA"));

        assertThat(approved.status()).as(approved.toString()).isEqualTo(200);
        assertThat(approved.text("estadoCuenta")).isEqualTo("ACTIVA");
        assertThat(api.login(correo, PASSWORD).status()).isEqualTo(200);
        Map<String, Object> row = row(id);
        assertThat(((Number) row.get("revisado_por_id")).longValue()).isEqualTo(admin.id());
        assertThat(row.get("revisado_en")).isNotNull();
        assertThat(audits(id, "APROBAR_CUENTA")).isEqualTo(1);
    }

    @Test
    void rechazarExigeMotivo() {
        String correo = registerPending();
        long id = idOf(correo);

        Response missing = decide(id, adminToken, Map.of("estadoCuenta", "RECHAZADA"));
        assertThat(missing.status()).isEqualTo(400);
        assertThat(missing.json().get("errores").get(0).get("campo").asString()).isEqualTo("motivo");
        assertThat(row(id).get("estado_cuenta")).isEqualTo("PENDIENTE");

        Response rejected = decide(id, adminToken, Map.of("estadoCuenta", "RECHAZADA", "motivo", "No es estudiante"));
        assertThat(rejected.status()).as(rejected.toString()).isEqualTo(200);
        assertThat(row(id).get("motivo_rechazo")).isEqualTo("No es estudiante");
        assertThat(audits(id, "RECHAZAR_CUENTA")).isEqualTo(1);
        Response login = api.login(correo, PASSWORD);
        assertThat(login.status()).isEqualTo(403);
        assertThat(login.codigo()).isEqualTo("CUENTA_RECHAZADA");
    }

    @Test
    void rechazadaPuedeAprobarseDespues() {
        long id = idOf(registerPending());
        assertThat(decide(id, adminToken, Map.of("estadoCuenta", "RECHAZADA", "motivo", "Duda")).status()).isEqualTo(200);

        assertThat(decide(id, adminToken, Map.of("estadoCuenta", "ACTIVA")).status()).isEqualTo(200);
        assertThat(row(id).get("estado_cuenta")).isEqualTo("ACTIVA");
        assertThat(row(id).get("motivo_rechazo")).isNull();
    }

    @Test
    void decidirCuentaActivaDevuelve409() {
        Account student = data.user("ESTUDIANTE", null);

        Response r = decide(student.id(), adminToken, Map.of("estadoCuenta", "ACTIVA"));

        assertThat(r.status()).isEqualTo(409);
        assertThat(r.codigo()).isEqualTo("CUENTA_NO_PENDIENTE");
    }

    @Test
    void bandejaYDecisionSoloAdmin() {
        String supervisorToken = api.token(data.user("SUPERVISOR", data.area()));
        long id = idOf(registerPending());

        assertThat(api.get("/api/v1/admin/users/pending", supervisorToken).status()).isEqualTo(403);
        assertThat(decide(id, supervisorToken, Map.of("estadoCuenta", "ACTIVA")).status()).isEqualTo(403);
        assertThat(row(id).get("estado_cuenta")).isEqualTo("PENDIENTE");
    }

    @Test
    void restablecerContrasena() {
        Account student = data.user("ESTUDIANTE", null);
        String oldToken = api.token(student);

        Response reset = api.post("/api/v1/admin/users/" + student.id() + "/password-reset", adminToken, null);

        assertThat(reset.status()).as(reset.toString()).isEqualTo(200);
        assertThat(reset.header("Cache-Control").orElse("")).contains("no-store");
        String temporal = reset.text("contrasenaTemporal");
        assertThat(temporal).hasSize(12).matches(".*[A-Za-z].*").matches(".*\\d.*");
        assertThat(api.get("/api/v1/requests", oldToken).status()).isEqualTo(401);
        Response login = api.login(student.correo(), temporal);
        assertThat(login.status()).isEqualTo(200);
        assertThat(login.json().get("debeCambiarContrasena").asBoolean()).isTrue();
        Response blocked = api.get("/api/v1/requests", login.text("tokenAcceso"));
        assertThat(blocked.status()).isEqualTo(403);
        assertThat(blocked.codigo()).isEqualTo("CAMBIO_CONTRASENA_REQUERIDO");
        assertThat(audits(student.id(), "RESTABLECER_CONTRASENA")).isEqualTo(1);
        long leaks = data.jdbc().sql("""
                SELECT COUNT(*) FROM auditoria_eventos WHERE entidad_id = :id
                AND (COALESCE(valor_anterior, '') LIKE :t OR COALESCE(valor_nuevo, '') LIKE :t)
                """).param("id", student.id()).param("t", "%" + temporal + "%").query(Long.class).single();
        assertThat(leaks).isZero();
    }

    @Test
    void restablecerSinPermisoOInvalido() {
        Account student = data.user("ESTUDIANTE", null);
        String technicianToken = api.token(data.user("TECNICO", data.area()));
        assertThat(api.post("/api/v1/admin/users/" + student.id() + "/password-reset", technicianToken, null).status())
                .isEqualTo(403);
        assertThat(api.post("/api/v1/admin/users/" + admin.id() + "/password-reset", adminToken, null).status())
                .isEqualTo(403);
        long pending = idOf(registerPending());
        assertThat(api.post("/api/v1/admin/users/" + pending + "/password-reset", adminToken, null).status()).isEqualTo(409);
        Account inactive = data.user("ESTUDIANTE", null);
        data.jdbc().sql("UPDATE usuarios SET activo = 0 WHERE id = :id").param("id", inactive.id()).update();
        assertThat(api.post("/api/v1/admin/users/" + inactive.id() + "/password-reset", adminToken, null).status()).isEqualTo(409);
        assertThat(api.post("/api/v1/admin/users/999999999/password-reset", adminToken, null).status()).isEqualTo(404);
    }
}
