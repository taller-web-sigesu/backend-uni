package pe.edu.universidad.servicios.requests;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import pe.edu.universidad.servicios.support.Api.Response;
import pe.edu.universidad.servicios.support.IntegrationTest;
import pe.edu.universidad.servicios.support.TestData.Account;
import tools.jackson.databind.JsonNode;

/**
 * Verificación de la fase 02 (HU-03 a HU-11, HU-19 y HU-20): flujo completo, alcance y conflictos.
 */
class RequestLifecycleIT extends IntegrationTest {

    private long area;
    private long category;
    private Account student;
    private Account otherStudent;
    private Account technician;
    private Account otherTechnician;
    private Account supervisor;
    private Account otherSupervisor;
    private String studentToken;
    private String technicianToken;
    private String supervisorToken;

    @BeforeEach
    void setUp() {
        area = data.area();
        category = data.category(area, 2);
        student = data.user("ESTUDIANTE", null);
        otherStudent = data.user("DOCENTE", null);
        technician = data.user("TECNICO", area);
        otherTechnician = data.user("TECNICO", area);
        supervisor = data.user("SUPERVISOR", area);
        otherSupervisor = data.user("SUPERVISOR", data.area());
        studentToken = api.token(student);
        technicianToken = api.token(technician);
        supervisorToken = api.token(supervisor);
    }

    private String createRequest(String token) {
        Response created = api.post("/api/v1/requests", token,
                Map.of("categoriaId", String.valueOf(category), "ubicacion", "Pabellón B, aula 204", "descripcion", "Sin Wi-Fi."));
        assertThat(created.status()).as(created.toString()).isEqualTo(201);
        return created.text("id");
    }

    @Test
    void recorridoCompletoDesdeRegistroHastaEncuesta() {
        Response created = api.post("/api/v1/requests", studentToken,
                Map.of("categoriaId", String.valueOf(category), "ubicacion", "Pabellón B", "descripcion", "Proyector dañado."));
        assertThat(created.status()).isEqualTo(201);
        assertThat(created.text("estado")).isEqualTo("NUEVO");
        assertThat(created.text("codigo")).matches("SOL-\\d{4}-\\d{6}");
        assertThat(created.text("areaId")).isEqualTo(String.valueOf(area));
        assertThat(created.text("prioridadId")).isEqualTo("2");
        assertThat(created.header("Location").orElseThrow()).endsWith("/api/v1/requests/" + created.text("id"));
        String id = created.text("id");

        Response detail = api.get("/api/v1/requests/" + id, studentToken);
        assertThat(detail.json().get("sla").get("estado").asString()).isEqualTo("EN_PLAZO");
        assertThat(detail.json().get("sla").get("fechaLimite").asString()).isNotBlank();

        Response eligible = api.get("/api/v1/requests/" + id + "/eligible-technicians", supervisorToken);
        assertThat(eligible.json().get("totalElements").asLong()).isEqualTo(2);

        Response assigned = api.post("/api/v1/requests/" + id + "/assignments", supervisorToken,
                Map.of("tecnicoId", String.valueOf(technician.id()), "prioridadId", "3"));
        assertThat(assigned.status()).as(assigned.toString()).isEqualTo(201);
        assertThat(assigned.text("estadoSolicitud")).isEqualTo("ASIGNADO");
        assertThat(assigned.text("prioridadId")).isEqualTo("3");

        Response inbox = api.get("/api/v1/requests", technicianToken);
        assertThat(ids(inbox)).contains(id);

        assertThat(api.patch("/api/v1/requests/" + id + "/status", technicianToken, Map.of("estado", "EN_PROCESO")).status()).isEqualTo(200);
        assertThat(api.patch("/api/v1/requests/" + id + "/status", technicianToken, Map.of("estado", "RESUELTO")).status()).isEqualTo(200);
        Response closed = api.patch("/api/v1/requests/" + id + "/status", supervisorToken, Map.of("estado", "CERRADO"));
        assertThat(closed.status()).isEqualTo(200);
        assertThat(closed.text("estado")).isEqualTo("CERRADO");
        assertThat(closed.json().get("responsableActual").isNull()).isTrue();

        // Tras el cierre sale de la bandeja del técnico y pierde acceso al detalle.
        assertThat(ids(api.get("/api/v1/requests", technicianToken))).doesNotContain(id);
        assertThat(api.get("/api/v1/requests/" + id, technicianToken).status()).isEqualTo(404);

        Response finalDetail = api.get("/api/v1/requests/" + id, supervisorToken);
        assertThat(finalDetail.json().get("ultimoResponsable").get("id").asString()).isEqualTo(String.valueOf(technician.id()));
        assertThat(finalDetail.json().get("sla").get("estado").asString()).isEqualTo("CERRADA");

        Response history = api.get("/api/v1/requests/" + id + "/history", studentToken);
        List<String> states = new ArrayList<>();
        history.json().get("items").forEach(i -> states.add(i.get("estado").asString()));
        assertThat(states).containsExactly("NUEVO", "ASIGNADO", "EN_PROCESO", "RESUELTO", "CERRADO");

        Response audit = api.get("/api/v1/requests/" + id + "/audit-events", supervisorToken);
        assertThat(audit.status()).isEqualTo(200);
        assertThat(audit.body()).contains("\"campo\":\"prioridad\"").contains("\"valorAnterior\":\"MEDIA\"").contains("\"valorNuevo\":\"ALTA\"");
        assertThat(api.get("/api/v1/requests/" + id + "/audit-events", api.token(otherSupervisor)).status()).isEqualTo(404);
        assertThat(api.get("/api/v1/requests/" + id + "/audit-events", studentToken).status()).isEqualTo(403);

        assertThat(api.get("/api/v1/requests/" + id, studentToken).json().get("encuestaRespondida").asBoolean()).isFalse();
        Response survey = api.post("/api/v1/requests/" + id + "/survey", studentToken, Map.of("calificacion", 5, "comentario", "Bien."));
        assertThat(survey.status()).isEqualTo(201);
        assertThat(api.get("/api/v1/requests/" + id, studentToken).json().get("encuestaRespondida").asBoolean()).isTrue();
        Response duplicated = api.post("/api/v1/requests/" + id + "/survey", studentToken, Map.of("calificacion", 4));
        assertThat(duplicated.status()).isEqualTo(409);
    }

    @Test
    void alcanceFueraDeRangoDevuelve404YNoSeCuenta() {
        String id = createRequest(studentToken);
        String otherToken = api.token(otherStudent);

        assertThat(api.get("/api/v1/requests/" + id, otherToken).status()).isEqualTo(404);
        assertThat(api.get("/api/v1/requests/" + id + "/comments", otherToken).status()).isEqualTo(404);
        assertThat(api.get("/api/v1/requests", otherToken).json().get("totalElements").asLong()).isZero();
        assertThat(api.get("/api/v1/requests/" + id, api.token(otherTechnician)).status()).isEqualTo(404);
        assertThat(api.get("/api/v1/requests/" + id, api.token(otherSupervisor)).status()).isEqualTo(404);
        assertThat(ids(api.get("/api/v1/requests?size=100", api.token(otherSupervisor)))).doesNotContain(id);
        assertThat(api.get("/api/v1/requests", api.token(data.user("ADMINISTRADOR", null))).status()).isEqualTo(403);
    }

    @Test
    void registroValidaRolCamposYCategoria() {
        assertThat(api.post("/api/v1/requests", technicianToken,
                Map.of("categoriaId", String.valueOf(category), "ubicacion", "A", "descripcion", "B")).status()).isEqualTo(403);
        assertThat(api.post("/api/v1/requests", studentToken,
                Map.of("categoriaId", String.valueOf(category), "ubicacion", " ", "descripcion", "B")).status()).isEqualTo(400);
        data.jdbc().sql("UPDATE categorias SET activo = 0 WHERE id = :id").param("id", category).update();
        assertThat(api.post("/api/v1/requests", studentToken,
                Map.of("categoriaId", String.valueOf(category), "ubicacion", "A", "descripcion", "B")).status()).isEqualTo(400);
    }

    @Test
    void comentariosRegistranAutorYFechaDeServidor() {
        String id = createRequest(studentToken);
        Response comment = api.post("/api/v1/requests/" + id + "/comments", studentToken, Map.of("contenido", "Sigue igual."));
        assertThat(comment.status()).isEqualTo(201);
        assertThat(comment.json().get("autor").get("id").asString()).isEqualTo(String.valueOf(student.id()));
        assertThat(api.post("/api/v1/requests/" + id + "/comments", studentToken, Map.of("contenido", "")).status()).isEqualTo(400);
        Response list = api.get("/api/v1/requests/" + id + "/comments", supervisorToken);
        assertThat(list.json().get("totalElements").asLong()).isEqualTo(1);
    }

    @Test
    void reglasDeTransicion() {
        String id = createRequest(studentToken);
        // Rechazo sin motivo
        assertThat(api.patch("/api/v1/requests/" + id + "/status", supervisorToken, Map.of("estado", "RECHAZADO")).status()).isEqualTo(400);
        // Salto de estado y rol sin permiso
        assertThat(api.patch("/api/v1/requests/" + id + "/status", supervisorToken, Map.of("estado", "CERRADO")).status()).isEqualTo(409);
        assertThat(api.patch("/api/v1/requests/" + id + "/status", supervisorToken, Map.of("estado", "EN_PROCESO")).status()).isEqualTo(403);
        // El técnico sin asignación no ve la solicitud
        assertThat(api.patch("/api/v1/requests/" + id + "/status", technicianToken, Map.of("estado", "EN_PROCESO")).status()).isEqualTo(404);

        api.post("/api/v1/requests/" + id + "/assignments", supervisorToken, Map.of("tecnicoId", String.valueOf(technician.id())));
        assertThat(api.patch("/api/v1/requests/" + id + "/status", technicianToken, Map.of("estado", "RESUELTO")).status()).isEqualTo(409);
        assertThat(api.patch("/api/v1/requests/" + id + "/status", technicianToken, Map.of("estado", "CERRADO")).status()).isEqualTo(403);

        Response rejected = api.patch("/api/v1/requests/" + id + "/status", supervisorToken,
                Map.of("estado", "RECHAZADO", "motivo", "Corresponde a otra sede."));
        assertThat(rejected.status()).isEqualTo(200);
        assertThat(api.get("/api/v1/requests/" + id, studentToken).text("motivoRechazo")).isEqualTo("Corresponde a otra sede.");
        // Terminal: sin nuevas transiciones, reasignaciones ni cambios de prioridad.
        assertThat(api.patch("/api/v1/requests/" + id + "/status", supervisorToken,
                Map.of("estado", "RECHAZADO", "motivo", "otra vez")).status()).isEqualTo(409);
        assertThat(api.post("/api/v1/requests/" + id + "/assignments", supervisorToken,
                Map.of("tecnicoId", String.valueOf(technician.id()))).status()).isEqualTo(409);
        assertThat(api.patch("/api/v1/requests/" + id, supervisorToken, Map.of("prioridadId", "4")).status()).isEqualTo(409);
        assertThat(api.post("/api/v1/requests/" + id + "/survey", studentToken, Map.of("calificacion", 5)).status()).isEqualTo(409);
    }

    @Test
    void asignacionValidaTecnicoYReasignacionConservaEstado() {
        String id = createRequest(studentToken);
        Account foreignTech = data.user("TECNICO", data.area());
        assertThat(api.post("/api/v1/requests/" + id + "/assignments", supervisorToken,
                Map.of("tecnicoId", String.valueOf(foreignTech.id()))).status()).isEqualTo(400);
        assertThat(api.post("/api/v1/requests/" + id + "/assignments", api.token(otherSupervisor),
                Map.of("tecnicoId", String.valueOf(technician.id()))).status()).isEqualTo(404);

        api.post("/api/v1/requests/" + id + "/assignments", supervisorToken, Map.of("tecnicoId", String.valueOf(technician.id())));
        api.patch("/api/v1/requests/" + id + "/status", technicianToken, Map.of("estado", "EN_PROCESO"));
        Response reassigned = api.post("/api/v1/requests/" + id + "/assignments", supervisorToken,
                Map.of("tecnicoId", String.valueOf(otherTechnician.id())));
        assertThat(reassigned.status()).isEqualTo(201);
        assertThat(reassigned.text("estadoSolicitud")).isEqualTo("EN_PROCESO");
        assertThat(api.get("/api/v1/requests/" + id, technicianToken).status()).isEqualTo(404);
        long active = data.jdbc().sql("SELECT COUNT(*) FROM asignaciones WHERE solicitud_id = :id AND finalizada_en IS NULL")
                .param("id", Long.parseLong(id)).query(Long.class).single();
        assertThat(active).isEqualTo(1);
    }

    @Test
    void cambioDePrioridadConservaElSla() {
        String id = createRequest(studentToken);
        String before = api.get("/api/v1/requests/" + id, supervisorToken).json().get("sla").get("fechaLimite").asString();
        Response changed = api.patch("/api/v1/requests/" + id, supervisorToken, Map.of("prioridadId", "4"));
        assertThat(changed.status()).isEqualTo(200);
        assertThat(changed.text("prioridadId")).isEqualTo("4");
        String after = api.get("/api/v1/requests/" + id, supervisorToken).json().get("sla").get("fechaLimite").asString();
        assertThat(after).isEqualTo(before);
        assertThat(api.patch("/api/v1/requests/" + id, supervisorToken, Map.of("prioridadId", "999")).status()).isEqualTo(400);
    }

    @Test
    void asignacionesConcurrentesDejanUnaSolaVigente() throws Exception {
        String id = createRequest(studentToken);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Callable<Response>> calls = List.of(
                    () -> api.post("/api/v1/requests/" + id + "/assignments", supervisorToken, Map.of("tecnicoId", String.valueOf(technician.id()))),
                    () -> api.post("/api/v1/requests/" + id + "/assignments", supervisorToken, Map.of("tecnicoId", String.valueOf(otherTechnician.id()))));
            for (Future<Response> f : pool.invokeAll(calls)) {
                assertThat(f.get().status()).isIn(201, 409);
            }
        } finally {
            pool.shutdownNow();
        }
        long active = data.jdbc().sql("SELECT COUNT(*) FROM asignaciones WHERE solicitud_id = :id AND finalizada_en IS NULL")
                .param("id", Long.parseLong(id)).query(Long.class).single();
        long historyAssigned = data.jdbc().sql("SELECT COUNT(*) FROM historial_estados WHERE solicitud_id = :id AND estado_destino_id = 2")
                .param("id", Long.parseLong(id)).query(Long.class).single();
        assertThat(active).isEqualTo(1);
        assertThat(historyAssigned).isEqualTo(1);
    }

    @Test
    void bandejaDelTecnicoOrdenaPorPrioridadYFecha() {
        String low = createRequest(studentToken);
        String high = createRequest(studentToken);
        api.post("/api/v1/requests/" + low + "/assignments", supervisorToken, Map.of("tecnicoId", String.valueOf(technician.id()), "prioridadId", "1"));
        api.post("/api/v1/requests/" + high + "/assignments", supervisorToken, Map.of("tecnicoId", String.valueOf(technician.id()), "prioridadId", "4"));
        assertThat(ids(api.get("/api/v1/requests", technicianToken))).containsExactly(high, low);
    }

    @Test
    void supervisorFiltraPorEstado() {
        String id = createRequest(studentToken);
        assertThat(ids(api.get("/api/v1/requests?estado=NUEVO&size=100", supervisorToken))).contains(id);
        assertThat(ids(api.get("/api/v1/requests?estado=CERRADO&size=100", supervisorToken))).doesNotContain(id);
        assertThat(api.get("/api/v1/requests?estado=XYZ", supervisorToken).status()).isEqualTo(400);
        assertThat(api.get("/api/v1/requests?size=101", supervisorToken).status()).isEqualTo(400);
    }

    private static List<String> ids(Response response) {
        List<String> ids = new ArrayList<>();
        JsonNode items = response.json().get("items");
        items.forEach(i -> ids.add(i.get("id").asString()));
        return ids;
    }

    @Test
    void asignadoPorStatusDevuelve400() {
        String id = createRequest(studentToken);
        Response r = api.patch("/api/v1/requests/" + id + "/status", supervisorToken, Map.of("estado", "ASIGNADO"));
        assertThat(r.status()).as(r.toString()).isEqualTo(400);
        assertThat(r.codigo()).isEqualTo("USAR_ASIGNACION");
    }

    @Test
    void adminRecibe403EnDetalleDeSolicitud() {
        String id = createRequest(studentToken);
        assertThat(api.get("/api/v1/requests/" + id, api.token(data.user("ADMINISTRADOR", null))).status()).isEqualTo(403);
    }
}
