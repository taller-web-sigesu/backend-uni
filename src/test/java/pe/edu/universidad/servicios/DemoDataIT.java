package pe.edu.universidad.servicios;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import pe.edu.universidad.servicios.support.Api.Response;
import pe.edu.universidad.servicios.support.IntegrationTest;

/**
 * La carga de datos ficticios es explícita y permite el recorrido de humo con cuentas de demostración.
 */
@TestPropertySource(properties = "spring.flyway.locations=classpath:db/migration,classpath:db/demo")
class DemoDataIT extends IntegrationTest {

    @Test
    void cuentaDemoIniciaSesionYAccedeARutaProtegida() {
        Response login = api.login("admin.demo@universidad.example", "Demo2026!");
        assertThat(login.status()).as(login.toString()).isEqualTo(200);
        assertThat(login.json().get("debeCambiarContrasena").asBoolean()).isFalse();
        assertThat(api.get("/api/v1/admin/users", login.text("tokenAcceso")).status()).isEqualTo(200);
        assertThat(api.get("/api/v1/admin/users", null).status()).isEqualTo(401);
        long applied = data.jdbc().sql("SELECT COUNT(*) FROM flyway_schema_history WHERE version = '1000' AND success = 1")
                .query(Long.class).single();
        assertThat(applied).isEqualTo(1);
    }
}
