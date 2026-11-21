package pe.edu.universidad.servicios;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.context.TestPropertySource;

import pe.edu.universidad.servicios.support.Api.Response;
import pe.edu.universidad.servicios.support.IntegrationTest;
import tools.jackson.databind.JsonNode;

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
        assertThat(data.jdbc().sql("""
                SELECT version FROM flyway_schema_history WHERE version IN ('1000', '1001') AND success = 1
                """).query(String.class).list()).containsExactlyInAnyOrder("1000", "1001");
    }

    @ParameterizedTest
    @CsvSource({"LAB-10, Laboratorio 10, 1", "CANCHA-01, Cancha deportiva, 3"})
    void ambienteDemoApareceConDisponibilidad(String code, String name, String areaId) {
        Response login = api.login("ana.demo@universidad.example", "Demo2026!");
        assertThat(login.status()).as(login.toString()).isEqualTo(200);
        String token = login.text("tokenAcceso");
        String date = "2030-03-05"; // Martes en el calendario de Lima.
        Response list = api.get("/api/v1/spaces?size=100&fecha=" + date, token);
        assertThat(list.status()).as(list.toString()).isEqualTo(200);
        JsonNode space = list.json().get("items").valueStream()
                .filter(item -> code.equals(item.get("codigo").asString()))
                .findFirst().orElseThrow();
        assertThat(space.get("nombre").asString()).isEqualTo(name);
        assertThat(space.get("areaId").asString()).isEqualTo(areaId);

        Response availability = api.get("/api/v1/spaces/" + space.get("id").asString()
                + "/availability?fecha=" + date, token);
        assertThat(availability.status()).as(availability.toString()).isEqualTo(200);
        JsonNode free = availability.json().get("libres");
        assertThat(free.size()).isEqualTo(1);
        assertThat(free.get(0).get("horaInicio").asString()).isEqualTo("07:00");
        assertThat(free.get(0).get("horaFin").asString()).isEqualTo("22:00");
    }

    @ParameterizedTest
    @CsvSource({
        "supervisor.sg.demo@universidad.example, SUPERVISOR",
        "tecnico.sg.demo@universidad.example, TECNICO"
    })
    void personalDemoDeServiciosGeneralesIniciaSesion(String email, String role) {
        Response login = api.login(email, "Demo2026!");
        assertThat(login.status()).as(login.toString()).isEqualTo(200);
        assertThat(login.json().get("debeCambiarContrasena").asBoolean()).isFalse();
        JsonNode user = login.json().get("usuario");
        assertThat(user.get("rol").asString()).isEqualTo(role);
        assertThat(user.get("areaId").asString()).isEqualTo("3");
        assertThat(user.get("areaNombre").asString()).isEqualTo("Servicios generales");
        assertThat(api.get("/api/v1/requests", login.text("tokenAcceso")).status()).isEqualTo(200);
    }
}
