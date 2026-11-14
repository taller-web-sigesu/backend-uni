package pe.edu.universidad.servicios.requests;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import pe.edu.universidad.servicios.support.Api.Response;
import pe.edu.universidad.servicios.support.IntegrationTest;
import pe.edu.universidad.servicios.support.TestData.Account;

/**
 * HU-12 a HU-14 (indicadores y CSV) y HU-18 (SLA): precedencia, límites y plazo congelado.
 */
class SlaAndDashboardIT extends IntegrationTest {

    private long area;
    private long category;
    private Account student;
    private Account technician;
    private Account supervisor;
    private Account admin;

    @BeforeEach
    void setUp() {
        area = data.area();
        category = data.category(area, 2);
        student = data.user("ESTUDIANTE", null);
        technician = data.user("TECNICO", area);
        supervisor = data.user("SUPERVISOR", area);
        admin = data.user("ADMINISTRADOR", null);
    }

    private String create(String token) {
        Response created = api.post("/api/v1/requests", token,
                Map.of("categoriaId", String.valueOf(category), "ubicacion", "Lab 1", "descripcion", "Falla de red."));
        assertThat(created.status()).as(created.toString()).isEqualTo(201);
        return created.text("id");
    }

    @Test
    void politicaDeCategoriaPrevaleceYCambiosNoAfectanSolicitudesExistentes() {
        String adminToken = api.token(admin);
        Response policy = api.post("/api/v1/admin/sla-policies", adminToken,
                Map.of("categoriaId", String.valueOf(category), "horasObjetivo", 10, "horasAviso", 2,
                        "vigenteDesde", Instant.now().minus(1, ChronoUnit.HOURS).toString()));
        assertThat(policy.status()).as(policy.toString()).isEqualTo(201);

        String studentToken = api.token(student);
        Instant before = Instant.now();
        String id = create(studentToken);
        Instant limite = Instant.parse(api.get("/api/v1/requests/" + id, studentToken).json().get("sla").get("fechaLimite").asString());
        // Categoría (10 h) y no prioridad MEDIA (48 h).
        assertThat(Duration.between(before, limite).toHours()).isBetween(9L, 10L);

        Response superposed = api.post("/api/v1/admin/sla-policies", adminToken,
                Map.of("categoriaId", String.valueOf(category), "horasObjetivo", 5, "horasAviso", 1));
        assertThat(superposed.status()).isEqualTo(409);
        Response invalid = api.post("/api/v1/admin/sla-policies", adminToken,
                Map.of("categoriaId", String.valueOf(category), "prioridadId", "1", "horasObjetivo", 5, "horasAviso", 1));
        assertThat(invalid.status()).isEqualTo(400);
        assertThat(api.post("/api/v1/admin/sla-policies", adminToken,
                Map.of("prioridadId", "1", "horasObjetivo", 5, "horasAviso", 5)).status()).isEqualTo(400);

        Response updated = api.patch("/api/v1/admin/sla-policies/" + policy.text("id"), adminToken, Map.of("horasObjetivo", 30));
        assertThat(updated.status()).isEqualTo(200);
        assertThat(api.get("/api/v1/requests/" + id, studentToken).json().get("sla").get("fechaLimite").asString())
                .isEqualTo(limite.toString());
    }

    @Test
    void estadoSlaPasaAPorVencerYVencida() {
        String studentToken = api.token(student);
        String id = create(studentToken);
        // Prioridad MEDIA: 48 h de objetivo y aviso 8 h antes.
        clock.advance(Duration.ofHours(39));
        assertThat(slaState(id, studentToken)).isEqualTo("EN_PLAZO");
        clock.advance(Duration.ofHours(1).plusMinutes(1));
        assertThat(slaState(id, studentToken)).isEqualTo("POR_VENCER");
        clock.advance(Duration.ofHours(8));
        assertThat(slaState(id, studentToken)).isEqualTo("VENCIDA");
    }

    private String slaState(String id, String token) {
        return api.get("/api/v1/requests/" + id, api.token(student)).json().get("sla").get("estado").asString();
    }

    @Test
    void resumenRespetaRangoAlcanceYUltimoResponsable() {
        Instant t0 = Instant.parse("2030-03-01T10:00:00Z");
        clock.fixAt(t0);
        String studentToken = api.token(student);
        String supervisorToken = api.token(supervisor);
        String technicianToken = api.token(technician);
        String atStart = create(studentToken);
        clock.fixAt(t0.plus(Duration.ofHours(1)));
        studentToken = api.token(student);
        create(studentToken);
        // Esta queda fuera del rango [t0, t0+1h).

        supervisorToken = api.token(supervisor);
        technicianToken = api.token(technician);
        api.post("/api/v1/requests/" + atStart + "/assignments", supervisorToken, Map.of("tecnicoId", String.valueOf(technician.id())));
        api.patch("/api/v1/requests/" + atStart + "/status", technicianToken, Map.of("estado", "EN_PROCESO"));
        clock.fixAt(t0.plus(Duration.ofHours(3)));
        studentToken = api.token(student);
        supervisorToken = api.token(supervisor);
        technicianToken = api.token(technician);
        api.patch("/api/v1/requests/" + atStart + "/status", technicianToken, Map.of("estado", "RESUELTO"));
        api.patch("/api/v1/requests/" + atStart + "/status", supervisorToken, Map.of("estado", "CERRADO"));

        String query = "fechaDesde=" + t0 + "&fechaHasta=" + t0.plus(Duration.ofHours(1));
        Response summary = api.get("/api/v1/dashboard/summary?" + query, supervisorToken);
        assertThat(summary.status()).as(summary.toString()).isEqualTo(200);
        assertThat(summary.json().get("registradas").asLong()).isEqualTo(1);
        assertThat(summary.json().get("atendidas").asLong()).isEqualTo(1);
        assertThat(summary.json().get("pendientes").asLong()).isZero();
        assertThat(summary.json().get("porcentajeResueltas").asDouble()).isEqualTo(100.0);
        assertThat(summary.json().get("promedioHorasAtencion").asDouble()).isEqualTo(3.0);
        assertThat(summary.json().get("porResponsable").get(0).get("id").asString()).isEqualTo(String.valueOf(technician.id()));
        assertThat(summary.json().get("filtros").get("areaId").asString()).isEqualTo(String.valueOf(area));

        String wide = "fechaDesde=" + t0 + "&fechaHasta=" + t0.plus(Duration.ofDays(1));
        assertThat(api.get("/api/v1/dashboard/summary?" + wide, supervisorToken).json().get("registradas").asLong()).isEqualTo(2);
        String adminToken = api.token(admin);
        assertThat(api.get("/api/v1/dashboard/summary?" + wide + "&areaId=" + area, adminToken).json().get("registradas").asLong())
                .isEqualTo(2);

        assertThat(api.get("/api/v1/dashboard/summary?" + query + "&areaId=" + data.area(), supervisorToken).status()).isEqualTo(403);
        assertThat(api.get("/api/v1/dashboard/summary?fechaDesde=" + t0 + "&fechaHasta=" + t0, supervisorToken).status()).isEqualTo(400);
        assertThat(api.get("/api/v1/dashboard/summary?" + query, studentToken).status()).isEqualTo(403);

        Response csv = api.get("/api/v1/dashboard/reports?tipo=solicitudes&" + wide, supervisorToken);
        assertThat(csv.status()).isEqualTo(200);
        assertThat(csv.header("Content-Type").orElse("")).startsWith("text/csv");
        assertThat(csv.header("Content-Disposition").orElse("")).contains("solicitudes.csv");
        assertThat(csv.body().split("\r\n")).hasSize(3);
        assertThat(csv.body().split("\r\n")[0]).isEqualTo("\uFEFFcodigo,categoria,area,estado,prioridad,responsable,creadoEn");

        Response indicators = api.get("/api/v1/dashboard/reports?tipo=indicadores&" + query, supervisorToken);
        assertThat(indicators.body()).contains("registradas,,1").contains("atendidas,,1");
        assertThat(api.get("/api/v1/dashboard/reports?tipo=otro&" + query, supervisorToken).status()).isEqualTo(400);
    }

    @Test
    void csvSinDatosSoloTieneEncabezados() {
        String query = "fechaDesde=2001-01-01T00:00:00Z&fechaHasta=2001-01-02T00:00:00Z";
        Response csv = api.get("/api/v1/dashboard/reports?tipo=solicitudes&" + query, api.token(supervisor));
        assertThat(csv.status()).isEqualTo(200);
        assertThat(csv.body()).isEqualTo("\uFEFFcodigo,categoria,area,estado,prioridad,responsable,creadoEn\r\n");
    }
}
