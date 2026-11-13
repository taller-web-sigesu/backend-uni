package pe.edu.universidad.servicios.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import pe.edu.universidad.servicios.support.Api.Response;
import pe.edu.universidad.servicios.support.IntegrationTest;

/**
 * HU-27: auto-registro de estudiantes con aprobación pendiente y login según el estado de la cuenta.
 */
class RegistrationIT extends IntegrationTest {

    static final String MENSAJE = "Registro enviado. Podrás ingresar cuando un administrador apruebe tu cuenta";
    static final String PASSWORD = "Registro2026x";

    static String institutionalEmail() {
        return "est." + UUID.randomUUID().toString().substring(0, 8) + "@unmsm.edu.pe";
    }

    static Map<String, Object> body(String correo) {
        Map<String, Object> body = new HashMap<>();
        body.put("nombreCompleto", "Estudiante Registrado");
        body.put("correo", correo);
        body.put("contrasena", PASSWORD);
        body.put("confirmacionContrasena", PASSWORD);
        return body;
    }

    private Response register(Map<String, Object> body) {
        return api.post("/api/v1/auth/register", null, body);
    }

    private Map<String, Object> account(String correo) {
        return data.jdbc().sql("""
                SELECT r.codigo AS rol, u.area_id, u.estado_cuenta, u.origen_registro, u.debe_cambiar_contrasena, u.id
                FROM usuarios u JOIN roles r ON r.id = u.rol_id WHERE u.correo = :correo
                """).param("correo", correo).query().singleRow();
    }

    @Test
    void registroCreaCuentaPendienteEstudiante() {
        String correo = institutionalEmail();
        Response response = register(body(correo));

        assertThat(response.status()).as(response.toString()).isEqualTo(201);
        assertThat(response.text("mensaje")).isEqualTo(MENSAJE);
        Map<String, Object> row = account(correo);
        assertThat(row.get("rol")).isEqualTo("ESTUDIANTE");
        assertThat(row.get("area_id")).isNull();
        assertThat(row.get("estado_cuenta")).isEqualTo("PENDIENTE");
        assertThat(row.get("origen_registro")).isEqualTo("AUTO_REGISTRO");
        assertThat(((Number) row.get("debe_cambiar_contrasena")).intValue()).isZero();
        long audits = data.jdbc().sql("""
                SELECT COUNT(*) FROM auditoria_eventos WHERE entidad = 'usuarios' AND accion = 'REGISTRO' AND entidad_id = :id
                """).param("id", row.get("id")).query(Long.class).single();
        assertThat(audits).isEqualTo(1);
    }

    @Test
    void registroIgnoraRolEnviado() {
        String correo = institutionalEmail();
        Map<String, Object> body = body(correo);
        body.put("rol", "ADMINISTRADOR");

        assertThat(register(body).status()).isEqualTo(201);
        assertThat(account(correo).get("rol")).isEqualTo("ESTUDIANTE");
    }

    @Test
    void registroRechazaDominioAjeno() {
        Response response = register(body("alguien@gmail.com"));

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.json().get("errores").get(0).get("campo").asString()).isEqualTo("correo");
    }

    @Test
    void registroNormalizaCorreoYRechazaSubdominio() {
        String correo = institutionalEmail();
        assertThat(register(body(correo)).status()).isEqualTo(201);

        Response duplicate = register(body("  " + correo.toUpperCase() + " "));
        assertThat(duplicate.status()).isEqualTo(409);
        assertThat(duplicate.codigo()).isEqualTo("CORREO_REGISTRADO");
        assertThat(duplicate.body()).doesNotContain("Estudiante Registrado").doesNotContain("PENDIENTE");

        assertThat(register(body("x." + UUID.randomUUID().toString().substring(0, 6) + "@fisi.unmsm.edu.pe")).status())
                .isEqualTo(400);
    }

    @Test
    void registroValidaConfirmacionYPolitica() {
        Map<String, Object> mismatch = body(institutionalEmail());
        mismatch.put("confirmacionContrasena", "Distinta2026x");
        Response r1 = register(mismatch);
        assertThat(r1.status()).isEqualTo(400);
        assertThat(r1.json().get("errores").get(0).get("campo").asString()).isEqualTo("confirmacionContrasena");

        Map<String, Object> weak = body(institutionalEmail());
        weak.put("contrasena", "corta");
        weak.put("confirmacionContrasena", "corta");
        Response r2 = register(weak);
        assertThat(r2.status()).isEqualTo(400);
        assertThat(r2.json().get("errores").get(0).get("campo").asString()).isEqualTo("contrasena");
    }

    @Test
    void loginPendienteYRechazado() {
        String pendiente = institutionalEmail();
        assertThat(register(body(pendiente)).status()).isEqualTo(201);
        Response pending = api.login(pendiente, PASSWORD);
        assertThat(pending.status()).isEqualTo(403);
        assertThat(pending.codigo()).isEqualTo("CUENTA_PENDIENTE");
        assertThat(pending.headers().allValues("Set-Cookie")).isEmpty();

        String rechazada = institutionalEmail();
        assertThat(register(body(rechazada)).status()).isEqualTo(201);
        data.jdbc().sql("UPDATE usuarios SET estado_cuenta = 'RECHAZADA', motivo_rechazo = 'No es estudiante' WHERE correo = :c")
                .param("c", rechazada).update();
        Response rejected = api.login(rechazada, PASSWORD);
        assertThat(rejected.status()).isEqualTo(403);
        assertThat(rejected.codigo()).isEqualTo("CUENTA_RECHAZADA");
        assertThat(rejected.headers().allValues("Set-Cookie")).isEmpty();

        Response wrong = api.login(pendiente, "Incorrecta2026");
        assertThat(wrong.status()).isEqualTo(401);
        assertThat(wrong.codigo()).isEqualTo("CREDENCIALES_INVALIDAS");
    }

    @Test
    void registroDesdeOrigenAjenoDevuelve403() {
        Response response = api.send("POST", "/api/v1/auth/register", null, body(institutionalEmail()),
                Map.of("Origin", "https://malicioso.example"));

        // El filtro CORS rechaza el origen ajeno antes que OriginGuard, igual que en login y refresh.
        assertThat(response.status()).isEqualTo(403);
    }
}
