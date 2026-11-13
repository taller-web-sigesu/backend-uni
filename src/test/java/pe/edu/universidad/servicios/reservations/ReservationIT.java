package pe.edu.universidad.servicios.reservations;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import pe.edu.universidad.servicios.support.Api.Response;
import pe.edu.universidad.servicios.support.IntegrationTest;
import pe.edu.universidad.servicios.support.TestData.Account;

/**
 * Verificación de la fase 03 (HU-21 a HU-25). Reloj fijo: lunes 2030-03-04 08:00 en Lima (13:00 UTC).
 */
class ReservationIT extends IntegrationTest {

    private static final Instant MONDAY_8AM_LIMA = Instant.parse("2030-03-04T13:00:00Z");
    private static final String TUESDAY = "2030-03-05";

    private long area;
    private long space;
    private Account student;
    private Account teacher;
    private Account supervisor;
    private Account admin;
    private String studentToken;

    @BeforeEach
    void setUp() {
        clock.fixAt(MONDAY_8AM_LIMA);
        area = data.area();
        space = space(area, 20, true);
        student = data.user("ESTUDIANTE", null);
        teacher = data.user("DOCENTE", null);
        supervisor = data.user("SUPERVISOR", area);
        admin = data.user("ADMINISTRADOR", null);
        studentToken = api.token(student);
    }

    /** Espacio de prueba abierto de lunes a sábado de 07:00 a 22:00 (domingo cerrado). */
    private long space(Long areaId, int capacity, boolean withSchedule) {
        String code = "ESP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        KeyHolder keys = new GeneratedKeyHolder();
        data.jdbc().sql("""
                INSERT INTO espacios (tipo_espacio_id, area_id, codigo, nombre, pabellon, capacidad)
                VALUES (2, :area, :codigo, :nombre, 'B', :capacidad)
                """).param("area", areaId).param("codigo", code).param("nombre", "Lab " + code).param("capacidad", capacity)
                .update(keys, "id");
        long id = keys.getKey().longValue();
        if (withSchedule) {
            for (int day = 1; day <= 6; day++) {
                data.jdbc().sql("INSERT INTO horarios_espacio (espacio_id, dia_semana, hora_apertura, hora_cierre) VALUES (:e, :d, '07:00', '22:00')")
                        .param("e", id).param("d", day).update();
            }
        }
        return id;
    }

    private Response reserve(String token, long spaceId, String fecha, String inicio, String fin) {
        Map<String, Object> body = new HashMap<>();
        body.put("espacioId", String.valueOf(spaceId));
        body.put("fecha", fecha);
        body.put("horaInicio", inicio);
        body.put("horaFin", fin);
        body.put("motivo", "Sesión de laboratorio");
        body.put("numAsistentes", 10);
        return api.post("/api/v1/reservations", token, body);
    }

    @Test
    void altaDecisionYCancelacion() {
        Response created = reserve(studentToken, space, TUESDAY, "10:00", "11:00");
        assertThat(created.status()).as(created.toString()).isEqualTo(201);
        assertThat(created.text("estado")).isEqualTo("PENDIENTE");
        assertThat(created.text("codigo")).matches("RES-\\d{4}-\\d{6}");
        assertThat(created.text("horaInicio")).isEqualTo("10:00");
        String id = created.text("id");

        String supervisorToken = api.token(supervisor);
        Response inbox = api.get("/api/v1/reservations?vista=gestion", supervisorToken);
        assertThat(inbox.body()).contains("\"id\":\"" + id + "\"");
        assertThat(api.get("/api/v1/reservations?vista=gestion", studentToken).status()).isEqualTo(403);

        Response confirmed = api.patch("/api/v1/reservations/" + id + "/status", supervisorToken, Map.of("estado", "CONFIRMADA"));
        assertThat(confirmed.status()).as(confirmed.toString()).isEqualTo(200);
        assertThat(confirmed.text("estado")).isEqualTo("CONFIRMADA");
        assertThat(confirmed.text("estadoOperativo")).isEqualTo("PROXIMA");

        // Ni el administrador puede cancelar una reserva ajena.
        assertThat(api.patch("/api/v1/reservations/" + id + "/status", api.token(admin), Map.of("estado", "CANCELADA")).status())
                .isEqualTo(403);
        Response cancelled = api.patch("/api/v1/reservations/" + id + "/status", studentToken, Map.of("estado", "CANCELADA"));
        assertThat(cancelled.status()).isEqualTo(200);
        assertThat(cancelled.text("estado")).isEqualTo("CANCELADA");

        Response history = api.get("/api/v1/reservations/" + id + "/history", studentToken);
        assertThat(history.json().get("totalElements").asLong()).isEqualTo(3);
        assertThat(api.get("/api/v1/reservations/" + id, api.token(teacher)).status()).isEqualTo(404);
        assertThat(api.get("/api/v1/reservations/" + id, supervisorToken).status()).isEqualTo(200);
    }

    @Test
    void franjaLiberadaPuedeReservarseYCancelarseOtraVez() {
        for (int i = 0; i < 3; i++) {
            Response created = reserve(studentToken, space, TUESDAY, "12:00", "13:00");
            assertThat(created.status()).as("intento " + i + ": " + created).isEqualTo(201);
            Response cancelled = api.patch("/api/v1/reservations/" + created.text("id") + "/status", studentToken,
                    Map.of("estado", "CANCELADA"));
            assertThat(cancelled.status()).as(cancelled.toString()).isEqualTo(200);
        }
    }

    @Test
    void contiguasPermitidasYSolapadasRechazadas() {
        assertThat(reserve(studentToken, space, TUESDAY, "08:00", "09:00").status()).isEqualTo(201);
        assertThat(reserve(api.token(teacher), space, TUESDAY, "09:00", "10:00").status()).isEqualTo(201);
        Response overlap = reserve(api.token(data.user("ESTUDIANTE", null)), space, TUESDAY, "08:30", "09:30");
        assertThat(overlap.status()).isEqualTo(409);
        assertThat(overlap.codigo()).isEqualTo("FRANJA_OCUPADA");

        Response availability = api.get("/api/v1/spaces/" + space + "/availability?fecha=" + TUESDAY, studentToken);
        assertThat(availability.status()).isEqualTo(200);
        assertThat(availability.json().get("ocupadas").size()).isEqualTo(2);
        assertThat(availability.json().get("libres").get(0).get("horaInicio").asString()).isEqualTo("07:00");
        assertThat(availability.json().get("libres").get(0).get("horaFin").asString()).isEqualTo("08:00");
        assertThat(availability.json().get("libres").get(1).get("horaInicio").asString()).isEqualTo("10:00");
    }

    @Test
    void validacionesDeAlta() {
        assertThat(reserve(studentToken, space, "2030-03-04", "08:00", "09:00").codigo()).isEqualTo("FRANJA_PASADA");
        assertThat(reserve(studentToken, space, "2030-03-04", "08:30", "09:30").codigo()).isEqualTo("ANTICIPACION_INSUFICIENTE");
        assertThat(reserve(studentToken, space, "2030-04-10", "10:00", "11:00").codigo()).isEqualTo("ANTICIPACION_EXCEDIDA");
        assertThat(reserve(studentToken, space, TUESDAY, "10:00", "10:15").codigo()).isEqualTo("DURACION_INVALIDA");
        assertThat(reserve(studentToken, space, TUESDAY, "06:00", "07:30").codigo()).isEqualTo("FUERA_DE_HORARIO");
        assertThat(reserve(studentToken, space, "2030-03-10", "10:00", "11:00").codigo()).isEqualTo("ESPACIO_CERRADO");
        assertThat(reserve(studentToken, space, TUESDAY, "11:00", "10:00").status()).isEqualTo(400);
        long small = space(area, 5, true);
        Response capacity = reserve(studentToken, small, TUESDAY, "10:00", "11:00");
        assertThat(capacity.codigo()).isEqualTo("CAPACIDAD_EXCEDIDA");
        data.jdbc().sql("UPDATE espacios SET activo = 0 WHERE id = :id").param("id", small).update();
        assertThat(reserve(studentToken, small, TUESDAY, "10:00", "11:00").status()).isEqualTo(404);
        assertThat(api.get("/api/v1/spaces/" + small + "/availability?fecha=" + TUESDAY, studentToken).status()).isEqualTo(404);
    }

    @Test
    void limiteDiarioEntreEspaciosDistintos() {
        long other = space(area, 20, true);
        assertThat(reserve(studentToken, space, TUESDAY, "08:00", "09:00").status()).isEqualTo(201);
        assertThat(reserve(studentToken, other, TUESDAY, "10:00", "11:00").status()).isEqualTo(201);
        Response third = reserve(studentToken, other, TUESDAY, "14:00", "15:00");
        assertThat(third.status()).isEqualTo(409);
        assertThat(third.codigo()).isEqualTo("LIMITE_DIARIO");
    }

    @Test
    void altasConcurrentesNoCreanReservasCruzadas() throws Exception {
        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            tokens.add(api.token(data.user("ESTUDIANTE", null)));
        }
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Callable<Response>> calls = new ArrayList<>();
            for (String token : tokens) {
                calls.add(() -> reserve(token, space, TUESDAY, "15:00", "16:30"));
            }
            int created = 0;
            for (Future<Response> f : pool.invokeAll(calls)) {
                int status = f.get().status();
                assertThat(status).isIn(201, 409);
                if (status == 201) {
                    created++;
                }
            }
            assertThat(created).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void limiteDiarioConcurrenteNoSeSupera() throws Exception {
        long s2 = space(area, 20, true);
        long s3 = space(area, 20, true);
        ExecutorService pool = Executors.newFixedThreadPool(3);
        try {
            List<Callable<Response>> calls = List.of(
                    () -> reserve(studentToken, space, TUESDAY, "08:00", "09:00"),
                    () -> reserve(studentToken, s2, TUESDAY, "08:00", "09:00"),
                    () -> reserve(studentToken, s3, TUESDAY, "08:00", "09:00"));
            for (Future<Response> f : pool.invokeAll(calls)) {
                assertThat(f.get().status()).isIn(201, 409);
            }
        } finally {
            pool.shutdownNow();
        }
        long active = data.jdbc().sql("SELECT COUNT(*) FROM reservas WHERE solicitante_id = :u AND fecha = :f AND estado_id IN (1, 2)")
                .param("u", student.id()).param("f", java.time.LocalDate.parse(TUESDAY)).query(Long.class).single();
        assertThat(active).isEqualTo(2);
    }

    @Test
    void decisionesProhibidasYConflictos() {
        String supervisorToken = api.token(supervisor);
        // Autoaprobación
        Response own = reserve(supervisorToken, space, TUESDAY, "10:00", "11:00");
        assertThat(own.status()).isEqualTo(201);
        assertThat(api.patch("/api/v1/reservations/" + own.text("id") + "/status", supervisorToken, Map.of("estado", "CONFIRMADA")).status())
                .isEqualTo(403);
        // El propietario no decide sobre su propia reserva y otro supervisor no la ve.
        Response pending = reserve(studentToken, space, TUESDAY, "12:00", "13:00");
        String id = pending.text("id");
        assertThat(api.patch("/api/v1/reservations/" + id + "/status", studentToken, Map.of("estado", "CONFIRMADA")).status()).isEqualTo(403);
        assertThat(api.patch("/api/v1/reservations/" + id + "/status", api.token(data.user("SUPERVISOR", data.area())),
                Map.of("estado", "CONFIRMADA")).status()).isEqualTo(404);
        // Rechazo sin motivo no deja historial
        assertThat(api.patch("/api/v1/reservations/" + id + "/status", supervisorToken, Map.of("estado", "RECHAZADA")).status()).isEqualTo(400);
        assertThat(api.get("/api/v1/reservations/" + id + "/history", studentToken).json().get("totalElements").asLong()).isEqualTo(1);
        assertThat(api.patch("/api/v1/reservations/" + id + "/status", supervisorToken, Map.of("estado", "OTRO")).status()).isEqualTo(400);

        Response rejected = api.patch("/api/v1/reservations/" + id + "/status", supervisorToken,
                Map.of("estado", "RECHAZADA", "motivo", "No habilitado."));
        assertThat(rejected.status()).isEqualTo(200);
        assertThat(rejected.text("motivoRechazo")).isEqualTo("No habilitado.");
        assertThat(api.patch("/api/v1/reservations/" + id + "/status", supervisorToken, Map.of("estado", "CONFIRMADA")).status()).isEqualTo(409);
        assertThat(api.patch("/api/v1/reservations/" + id + "/status", studentToken, Map.of("estado", "CANCELADA")).status()).isEqualTo(409);

        // Espacio sin área: decide el administrador.
        long noArea = space(null, 20, true);
        Response r = reserve(studentToken, noArea, TUESDAY, "16:00", "17:00");
        assertThat(api.patch("/api/v1/reservations/" + r.text("id") + "/status", supervisorToken, Map.of("estado", "CONFIRMADA")).status())
                .isEqualTo(404);
        assertThat(api.patch("/api/v1/reservations/" + r.text("id") + "/status", api.token(admin), Map.of("estado", "CONFIRMADA")).status())
                .isEqualTo(200);
    }

    @Test
    void cancelacionTrasElInicioSeDeniega() {
        Response created = reserve(studentToken, space, TUESDAY, "10:00", "11:00");
        clock.fixAt(Instant.parse("2030-03-05T15:00:00Z")); // 10:00 en Lima
        studentToken = api.token(student);
        Response late = api.patch("/api/v1/reservations/" + created.text("id") + "/status", studentToken, Map.of("estado", "CANCELADA"));
        assertThat(late.status()).isEqualTo(409);
        assertThat(late.codigo()).isEqualTo("INICIO_ALCANZADO");
    }

    @Test
    void listadoDeEspaciosYAdministracion() {
        String adminToken = api.token(admin);
        Response list = api.get("/api/v1/spaces?pabellon=B&capacidadMinima=15&fecha=" + TUESDAY + "&size=100", studentToken);
        assertThat(list.status()).isEqualTo(200);
        assertThat(list.body()).contains("\"id\":\"" + space + "\"").contains("\"horaApertura\":\"07:00\"");
        assertThat(api.get("/api/v1/spaces?capacidadMinima=0", studentToken).status()).isEqualTo(400);
        assertThat(api.get("/api/v1/spaces?fecha=10-03-2030", studentToken).status()).isEqualTo(400);

        String code = "AULA-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        Response created = api.post("/api/v1/admin/spaces", adminToken,
                Map.of("codigo", code, "nombre", "Aula " + code, "tipoEspacioId", "1", "capacidad", 30, "areaId", String.valueOf(area), "pabellon", "C"));
        assertThat(created.status()).as(created.toString()).isEqualTo(201);
        assertThat(created.json().get("requiereAprobacion").asBoolean()).isTrue();
        assertThat(api.post("/api/v1/admin/spaces", adminToken,
                Map.of("codigo", code, "nombre", "Otro", "tipoEspacioId", "1", "capacidad", 30)).status()).isEqualTo(409);
        String spaceId = created.text("id");

        Response schedule = api.put("/api/v1/admin/spaces/" + spaceId + "/schedule", adminToken,
                Map.of("franjas", List.of(Map.of("diaSemana", 2, "horaApertura", "08:00", "horaCierre", "18:00"))));
        assertThat(schedule.status()).as(schedule.toString()).isEqualTo(200);
        assertThat(schedule.json().get("franjas").size()).isEqualTo(1);
        assertThat(api.put("/api/v1/admin/spaces/" + spaceId + "/schedule", adminToken,
                Map.of("franjas", List.of(Map.of("diaSemana", 2, "horaApertura", "08:00", "horaCierre", "18:00"),
                        Map.of("diaSemana", 2, "horaApertura", "09:00", "horaCierre", "10:00")))).status()).isEqualTo(400);

        Response day = api.get("/api/v1/spaces/" + spaceId + "/availability?fecha=2030-03-06", studentToken);
        assertThat(day.json().get("horario").isNull()).isTrue();
        assertThat(day.json().get("libres").size()).isZero();

        Response deactivated = api.patch("/api/v1/admin/spaces/" + spaceId, adminToken, Map.of("activo", false));
        assertThat(deactivated.json().get("activo").asBoolean()).isFalse();
        assertThat(api.get("/api/v1/admin/spaces/" + spaceId + "/schedule", adminToken).status()).isEqualTo(200);
    }

    @Test
    void politicaDeReservas() {
        // Con el reloj real, para que la nueva vigencia no empiece en el futuro de otras pruebas.
        clock.reset();
        String adminToken = api.token(admin);
        Response current = api.get("/api/v1/admin/reservation-policy", adminToken);
        assertThat(current.status()).isEqualTo(200);
        Map<String, Object> policy = new HashMap<>(Map.of("duracionMinimaMinutos", 30, "duracionMaximaMinutos", 240,
                "horasAnticipacionMinima", 1, "diasAnticipacionMaxima", 30, "maxReservasActivasPorDia", 3));
        Response replaced = api.put("/api/v1/admin/reservation-policy", adminToken, policy);
        assertThat(replaced.status()).as(replaced.toString()).isEqualTo(200);
        assertThat(replaced.json().get("maxReservasActivasPorDia").asInt()).isEqualTo(3);
        policy.put("duracionMaximaMinutos", 10);
        assertThat(api.put("/api/v1/admin/reservation-policy", adminToken, policy).status()).isEqualTo(400);
        policy.put("duracionMaximaMinutos", 240);
        policy.put("maxReservasActivasPorDia", 2);
        assertThat(api.put("/api/v1/admin/reservation-policy", adminToken, policy).status()).isEqualTo(200);
    }

    @Test
    void estadoOperativoDerivadoDelReloj() {
        Response created = reserve(studentToken, space, TUESDAY, "10:00", "11:00");
        String id = created.text("id");
        api.patch("/api/v1/reservations/" + id + "/status", api.token(supervisor), Map.of("estado", "CONFIRMADA"));
        clock.fixAt(Instant.parse("2030-03-05T15:30:00Z"));
        assertThat(api.get("/api/v1/reservations/" + id, api.token(student)).text("estadoOperativo")).isEqualTo("EN_CURSO");
        clock.advance(Duration.ofHours(1));
        assertThat(api.get("/api/v1/reservations/" + id, api.token(student)).text("estadoOperativo")).isEqualTo("FINALIZADA");
    }
}
