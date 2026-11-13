package pe.edu.universidad.servicios.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;

import pe.edu.universidad.servicios.support.Api.Response;
import pe.edu.universidad.servicios.support.IntegrationTest;
import pe.edu.universidad.servicios.support.TestData.Account;

/**
 * Verificación de la fase 01: login, primer ingreso, logout, cambio de contraseña, refresh y origen.
 */
class AuthIT extends IntegrationTest {

    private static final Map<String, String> ORIGIN_OK = Map.of("Origin", "http://localhost:3000");

    @Test
    void migracionesCreanVistasYSemillas() {
        long vistas = data.jdbc().sql("""
                SELECT COUNT(*) FROM information_schema.views WHERE table_schema = DATABASE()
                AND table_name IN ('vw_solicitudes_detalle', 'vw_estado_sla', 'vw_reservas_detalle',
                                   'vw_reservas_operativas', 'vw_ultimo_responsable')
                """).query(Long.class).single();
        assertThat(vistas).isEqualTo(5);
        assertThat(data.jdbc().sql("SELECT COUNT(*) FROM roles").query(Long.class).single()).isEqualTo(5);
        assertThat(data.jdbc().sql("SELECT COUNT(*) FROM transiciones_estado").query(Long.class).single()).isEqualTo(7);
        assertThat(data.jdbc().sql("SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1").query(Long.class).single())
                .isGreaterThanOrEqualTo(2);
    }

    @Test
    void loginValidoEmiteJwtCookieYNoStore() {
        Account student = data.user("ESTUDIANTE", null);
        Response response = api.login(student.correo(), student.password());

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.text("tokenAcceso")).isNotBlank();
        assertThat(response.json().get("expiraEnSegundos").asLong()).isEqualTo(900);
        assertThat(response.json().get("usuario").get("rol").asString()).isEqualTo("ESTUDIANTE");
        assertThat(response.json().get("usuario").get("id").isString()).isTrue();
        assertThat(response.header("Cache-Control").orElse("")).contains("no-store");
        String cookie = response.headers().allValues("Set-Cookie").stream().filter(c -> c.startsWith("refresh_token=")).findFirst().orElseThrow();
        assertThat(cookie).contains("HttpOnly").contains("SameSite=Lax").contains("Path=/api/v1/auth").contains("Secure");
        assertThat(response.header("X-Correlation-ID")).isPresent();
    }

    @Test
    void loginInvalidoOCuentaInactivaNoExponeInformacion() {
        Account student = data.user("ESTUDIANTE", null);
        Response wrong = api.login(student.correo(), "Incorrecta123");
        Response unknown = api.login("nadie@universidad.example", "Incorrecta123");
        data.jdbc().sql("UPDATE usuarios SET activo = 0 WHERE id = :id").param("id", student.id()).update();
        Response inactive = api.login(student.correo(), student.password());

        assertThat(wrong.status()).isEqualTo(401);
        assertThat(unknown.status()).isEqualTo(401);
        assertThat(inactive.status()).isEqualTo(401);
        assertThat(wrong.codigo()).isEqualTo(unknown.codigo()).isEqualTo(inactive.codigo());
        assertThat(wrong.text("detail")).isEqualTo(inactive.text("detail"));
        assertThat(wrong.header("Content-Type").orElse("")).contains("application/problem+json");
        assertThat(wrong.text("correlationId")).isNotBlank();
    }

    @Test
    void loginSinCamposDevuelve400() {
        Response response = api.post("/api/v1/auth/login", null, Map.of("correo", ""));
        assertThat(response.status()).isEqualTo(400);
        assertThat(response.json().get("errores").isArray()).isTrue();
    }

    @Test
    void limiteDeIntentosDevuelve429() {
        Account student = data.user("ESTUDIANTE", null);
        for (int i = 0; i < 5; i++) {
            assertThat(api.login(student.correo(), "Incorrecta123").status()).isEqualTo(401);
        }
        Response blocked = api.login(student.correo(), student.password());
        assertThat(blocked.status()).isEqualTo(429);
    }

    @Test
    void primerIngresoSoloPermiteCambioRenovacionYLogout() {
        Account student = data.user("ESTUDIANTE", null, true);
        Response login = api.login(student.correo(), student.password());
        assertThat(login.json().get("debeCambiarContrasena").asBoolean()).isTrue();
        String token = login.text("tokenAcceso");

        Response business = api.get("/api/v1/requests", token);
        assertThat(business.status()).isEqualTo(403);
        assertThat(business.codigo()).isEqualTo("CAMBIO_CONTRASENA_REQUERIDO");

        Response refreshed = api.send("POST", "/api/v1/auth/refresh", null, null,
                Map.of("Cookie", "refresh_token=" + login.refreshCookie()));
        assertThat(refreshed.status()).isEqualTo(200);
        assertThat(refreshed.json().get("debeCambiarContrasena").asBoolean()).isTrue();

        Response changed = api.patch("/api/v1/auth/password", refreshed.text("tokenAcceso"),
                Map.of("contrasenaActual", student.password(), "contrasenaNueva", "NuevaClave2026"));
        assertThat(changed.status()).isEqualTo(200);
        assertThat(changed.json().get("debeCambiarContrasena").asBoolean()).isFalse();

        assertThat(api.get("/api/v1/requests", changed.text("tokenAcceso")).status()).isEqualTo(200);
        // Las credenciales anteriores al cambio quedan revocadas.
        assertThat(api.get("/api/v1/requests", token).status()).isEqualTo(401);
        assertThat(api.get("/api/v1/requests", refreshed.text("tokenAcceso")).status()).isEqualTo(401);
    }

    @Test
    void cambioDeContrasenaValidaActualYPolitica() {
        Account student = data.user("ESTUDIANTE", null);
        String token = api.token(student);
        Response wrongCurrent = api.patch("/api/v1/auth/password", token,
                Map.of("contrasenaActual", "Otra2026x", "contrasenaNueva", "NuevaClave2026"));
        Response weak = api.patch("/api/v1/auth/password", token,
                Map.of("contrasenaActual", student.password(), "contrasenaNueva", "corta"));
        assertThat(wrongCurrent.status()).isEqualTo(400);
        assertThat(weak.status()).isEqualTo(400);
        assertThat(api.patch("/api/v1/auth/password", null,
                Map.of("contrasenaActual", "x", "contrasenaNueva", "y")).status()).isEqualTo(401);
    }

    @Test
    void logoutRevocaJwtYRefreshDeEsaSesion() {
        Account student = data.user("ESTUDIANTE", null);
        Response login = api.login(student.correo(), student.password());
        String token = login.text("tokenAcceso");

        Response logout = api.post("/api/v1/auth/logout", token, null);
        assertThat(logout.status()).isEqualTo(204);
        assertThat(logout.headers().allValues("Set-Cookie")).anyMatch(c -> c.startsWith("refresh_token=;") && c.contains("Max-Age=0"));

        assertThat(api.get("/api/v1/requests", token).status()).isEqualTo(401);
        Response refresh = api.send("POST", "/api/v1/auth/refresh", null, null,
                Map.of("Cookie", "refresh_token=" + login.refreshCookie()));
        assertThat(refresh.status()).isEqualTo(401);
        assertThat(api.post("/api/v1/auth/logout", null, null).status()).isEqualTo(401);
    }

    @Test
    void reusoDeRefreshRevocaLaSesion() {
        Account student = data.user("ESTUDIANTE", null);
        Response login = api.login(student.correo(), student.password());
        String first = login.refreshCookie();

        Response rotated = api.send("POST", "/api/v1/auth/refresh", null, null, Map.of("Cookie", "refresh_token=" + first));
        assertThat(rotated.status()).isEqualTo(200);
        String second = rotated.refreshCookie();
        assertThat(second).isNotEqualTo(first);

        Response reused = api.send("POST", "/api/v1/auth/refresh", null, null, Map.of("Cookie", "refresh_token=" + first));
        assertThat(reused.status()).isEqualTo(401);
        // La sesión quedó revocada de forma persistente: ni el refresh vigente ni el JWT sirven.
        assertThat(api.send("POST", "/api/v1/auth/refresh", null, null, Map.of("Cookie", "refresh_token=" + second)).status())
                .isEqualTo(401);
        assertThat(api.get("/api/v1/requests", rotated.text("tokenAcceso")).status()).isEqualTo(401);
    }

    @Test
    void refreshConcurrenteRotaComoMaximoUnaVez() throws Exception {
        Account student = data.user("ESTUDIANTE", null);
        String refresh = api.login(student.correo(), student.password()).refreshCookie();
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Callable<Integer>> calls = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                calls.add(() -> api.send("POST", "/api/v1/auth/refresh", null, null,
                        Map.of("Cookie", "refresh_token=" + refresh)).status());
            }
            long ok = 0;
            for (Future<Integer> f : pool.invokeAll(calls)) {
                if (f.get() == 200) {
                    ok++;
                }
            }
            assertThat(ok).isLessThanOrEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void rotacionNoAmpliaLosSieteDias() {
        Account student = data.user("ESTUDIANTE", null);
        Response login = api.login(student.correo(), student.password());
        clock.advance(Duration.ofDays(6));
        Response rotated = api.send("POST", "/api/v1/auth/refresh", null, null,
                Map.of("Cookie", "refresh_token=" + login.refreshCookie()));
        assertThat(rotated.status()).isEqualTo(200);
        clock.advance(Duration.ofDays(1).plusMinutes(1));
        Response expired = api.send("POST", "/api/v1/auth/refresh", null, null,
                Map.of("Cookie", "refresh_token=" + rotated.refreshCookie()));
        assertThat(expired.status()).isEqualTo(401);
    }

    @Test
    void origenNoAutorizadoNoPuedeUsarLaCookie() {
        Account student = data.user("ESTUDIANTE", null);
        Response login = api.login(student.correo(), student.password());
        Response evil = api.send("POST", "/api/v1/auth/refresh", null, null,
                Map.of("Cookie", "refresh_token=" + login.refreshCookie(), "Origin", "https://evil.example"));
        assertThat(evil.status()).isEqualTo(403);
        Response good = api.send("POST", "/api/v1/auth/refresh", null, null,
                Map.of("Cookie", "refresh_token=" + login.refreshCookie(), "Origin", ORIGIN_OK.get("Origin")));
        assertThat(good.status()).isEqualTo(200);
    }

    @Test
    void rutasProtegidasSinCredencialDevuelven401YEstadoEsPublico() {
        assertThat(api.get("/api/v1/requests", null).status()).isEqualTo(401);
        assertThat(api.get("/api/v1/requests", "token-invalido").status()).isEqualTo(401);
        Response status = api.get("/api/v1/status", null);
        assertThat(status.status()).isEqualTo(200);
        assertThat(status.text("status")).isEqualTo("UP");
        assertThat(api.get("/actuator/health", null).status()).isEqualTo(200);
    }

    @Test
    void desactivarCuentaRevocaSesiones() {
        Account admin = data.user("ADMINISTRADOR", null);
        Account student = data.user("ESTUDIANTE", null);
        String studentToken = api.token(student);
        Response patch = api.patch("/api/v1/admin/users/" + student.id(), api.token(admin), Map.of("activo", false));
        assertThat(patch.status()).isEqualTo(200);
        assertThat(api.get("/api/v1/requests", studentToken).status()).isEqualTo(401);
    }
}
