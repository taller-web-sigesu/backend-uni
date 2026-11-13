package pe.edu.universidad.servicios.attachments;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import pe.edu.universidad.servicios.shared.config.AppProperties;
import pe.edu.universidad.servicios.support.Api.Response;
import pe.edu.universidad.servicios.support.IntegrationTest;
import pe.edu.universidad.servicios.support.TestData.Account;

/**
 * HU-26: evidencias de una solicitud con almacenamiento local.
 */
class AttachmentIT extends IntegrationTest {

    static final int MAX = 5 * 1024 * 1024;

    @Autowired
    AppProperties properties;

    private long area;
    private long category;
    private Account student;
    private Account technician;
    private Account supervisor;
    private String studentToken;
    private String technicianToken;
    private String supervisorToken;

    static byte[] jpeg(int size) {
        byte[] content = new byte[size];
        Arrays.fill(content, (byte) 0x11);
        content[0] = (byte) 0xFF;
        content[1] = (byte) 0xD8;
        content[2] = (byte) 0xFF;
        return content;
    }

    static byte[] pdf(int size) {
        byte[] content = new byte[size];
        Arrays.fill(content, (byte) 0x20);
        System.arraycopy("%PDF-1.7".getBytes(), 0, content, 0, 8);
        return content;
    }

    @BeforeEach
    void setUp() {
        area = data.area();
        category = data.category(area, 2);
        student = data.user("ESTUDIANTE", null);
        technician = data.user("TECNICO", area);
        supervisor = data.user("SUPERVISOR", area);
        studentToken = api.token(student);
        technicianToken = api.token(technician);
        supervisorToken = api.token(supervisor);
    }

    private String createRequest() {
        Response created = api.post("/api/v1/requests", studentToken,
                Map.of("categoriaId", String.valueOf(category), "ubicacion", "Pabellón C", "descripcion", "Fuga de agua."));
        assertThat(created.status()).as(created.toString()).isEqualTo(201);
        return created.text("id");
    }

    private void assign(String id) {
        Response r = api.post("/api/v1/requests/" + id + "/assignments", supervisorToken,
                Map.of("tecnicoId", String.valueOf(technician.id())));
        assertThat(r.status()).as(r.toString()).isEqualTo(201);
    }

    private Response upload(String id, String token, String name, String type, byte[] content) {
        return api.multipart("/api/v1/requests/" + id + "/attachments", token, name, type, content);
    }

    private Path storedFile(String attachmentId) {
        String key = data.jdbc().sql("SELECT clave_objeto FROM adjuntos_solicitud WHERE id = :id")
                .param("id", Long.parseLong(attachmentId)).query(String.class).single();
        return Path.of(properties.storage().localDir()).resolve(key);
    }

    private long active(String id) {
        return data.jdbc().sql("SELECT COUNT(*) FROM adjuntos_solicitud WHERE solicitud_id = :id AND eliminado_en IS NULL")
                .param("id", Long.parseLong(id)).query(Long.class).single();
    }

    private long audits(String requestId, String accion) {
        return data.jdbc().sql("""
                SELECT COUNT(*) FROM auditoria_eventos WHERE entidad = 'adjuntos_solicitud' AND accion = :a AND solicitud_id = :id
                """).param("a", accion).param("id", Long.parseLong(requestId)).query(Long.class).single();
    }

    @Test
    void solicitanteAdjuntaYVeLaEvidencia() {
        String id = createRequest();
        byte[] content = jpeg(2 * 1024 * 1024);

        Response created = upload(id, studentToken, "foto-aula.jpg", "image/jpeg", content);

        assertThat(created.status()).as(created.toString()).isEqualTo(201);
        assertThat(created.text("nombre")).isEqualTo("foto-aula.jpg");
        assertThat(created.json().get("tamanoBytes").asLong()).isEqualTo(content.length);
        assertThat(created.text("tipoMime")).isEqualTo("image/jpeg");
        assertThat(created.text("autorId")).isEqualTo(String.valueOf(student.id()));
        assertThat(created.json().get("puedeEliminar").asBoolean()).isTrue();

        Response list = api.get("/api/v1/requests/" + id + "/attachments", studentToken);
        assertThat(list.status()).isEqualTo(200);
        assertThat(list.json().size()).isEqualTo(1);
        assertThat(list.json().get(0).get("autorNombre").asString()).isNotBlank();
        assertThat(list.json().get(0).get("creadoEn").asString()).isNotBlank();

        HttpResponse<byte[]> download = api.download("/api/v1/requests/" + id + "/attachments/" + created.text("id") + "/content",
                studentToken);
        assertThat(download.statusCode()).isEqualTo(200);
        assertThat(download.body()).isEqualTo(content);
        assertThat(download.headers().firstValue("Content-Type").orElse("")).startsWith("image/jpeg");
        assertThat(download.headers().firstValue("Content-Disposition").orElse("")).startsWith("attachment;")
                .contains("filename*=UTF-8''foto-aula.jpg");
        assertThat(audits(id, "ADJUNTAR")).isEqualTo(1);
    }

    @Test
    void rechazaContenidoNoPermitidoAunqueLaExtensionSeaJpg() {
        String id = createRequest();

        Response r = upload(id, studentToken, "foto.jpg", "image/jpeg", "MZ\u0090\u0000 programa".getBytes());

        assertThat(r.status()).isEqualTo(400);
        assertThat(r.json().get("errores").get(0).get("campo").asString()).isEqualTo("archivo");
        assertThat(r.json().get("errores").get(0).get("mensaje").asString()).isEqualTo("Solo se aceptan archivos JPG, PNG o PDF.");
        assertThat(active(id)).isZero();
        assertThat(Path.of(properties.storage().localDir()).resolve("solicitudes").resolve(id)).doesNotExist();
    }

    @Test
    void pdfDeclaradoComoPngSeGuardaConElTipoDetectado() {
        String id = createRequest();

        Response r = upload(id, studentToken, "informe.png", "image/png", pdf(1000));

        assertThat(r.status()).isEqualTo(201);
        assertThat(r.text("tipoMime")).isEqualTo("application/pdf");
        assertThat(storedFile(r.text("id")).getFileName().toString()).endsWith(".pdf");
    }

    @Test
    void rechazaArchivoMayorA5MB() {
        String id = createRequest();

        Response big = upload(id, studentToken, "plano.pdf", "application/pdf", pdf(8 * 1024 * 1024));
        assertThat(big.status()).as(big.toString()).isEqualTo(400);
        assertThat(big.json().get("errores").get(0).get("campo").asString()).isEqualTo("archivo");
        assertThat(big.json().get("errores").get(0).get("mensaje").asString()).isEqualTo("El tamaño máximo es 5 MB.");

        Response justOver = upload(id, studentToken, "plano.pdf", "application/pdf", pdf(MAX + 1));
        assertThat(justOver.status()).isEqualTo(400);

        assertThat(upload(id, studentToken, "limite.pdf", "application/pdf", pdf(MAX)).status()).isEqualTo(201);
        assertThat(upload(id, studentToken, "vacio.pdf", "application/pdf", new byte[0]).status()).isEqualTo(400);
    }

    @Test
    void maximoCincoVigentes() {
        String id = createRequest();
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Response r = upload(id, studentToken, "f" + i + ".jpg", "image/jpeg", jpeg(100));
            assertThat(r.status()).isEqualTo(201);
            ids.add(r.text("id"));
        }

        Response sixth = upload(id, studentToken, "f5.jpg", "image/jpeg", jpeg(100));
        assertThat(sixth.status()).isEqualTo(409);
        assertThat(sixth.codigo()).isEqualTo("LIMITE_EVIDENCIAS");

        assertThat(api.send("DELETE", "/api/v1/requests/" + id + "/attachments/" + ids.get(0), studentToken, null, Map.of())
                .status()).isEqualTo(204);
        assertThat(upload(id, studentToken, "f6.jpg", "image/jpeg", jpeg(100)).status()).isEqualTo(201);
    }

    @Test
    void cargasConcurrentesNoSuperanCinco() throws Exception {
        String id = createRequest();
        for (int i = 0; i < 4; i++) {
            assertThat(upload(id, studentToken, "f" + i + ".jpg", "image/jpeg", jpeg(100)).status()).isEqualTo(201);
        }
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Callable<Integer>> calls = List.of(
                    () -> upload(id, studentToken, "a.jpg", "image/jpeg", jpeg(100)).status(),
                    () -> upload(id, studentToken, "b.jpg", "image/jpeg", jpeg(100)).status());
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> f : pool.invokeAll(calls)) {
                statuses.add(f.get());
            }
            assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        } finally {
            pool.shutdownNow();
        }
        assertThat(active(id)).isEqualTo(5);
    }

    @Test
    void accesoIndebidoNoRevelaExistencia() {
        String id = createRequest();
        String attachment = upload(id, studentToken, "f.jpg", "image/jpeg", jpeg(100)).text("id");
        String otherToken = api.token(data.user("ESTUDIANTE", null));

        assertThat(api.get("/api/v1/requests/" + id + "/attachments", otherToken).status()).isEqualTo(404);
        assertThat(api.download("/api/v1/requests/" + id + "/attachments/" + attachment + "/content", otherToken).statusCode())
                .isEqualTo(404);
        assertThat(api.send("DELETE", "/api/v1/requests/" + id + "/attachments/" + attachment, otherToken, null, Map.of())
                .status()).isEqualTo(404);

        String ownOther = createRequest();
        assertThat(api.download("/api/v1/requests/" + ownOther + "/attachments/" + attachment + "/content", studentToken)
                .statusCode()).isEqualTo(404);
    }

    @Test
    void tecnicoYSupervisorAdjuntan() {
        String id = createRequest();
        Account otherTechnician = data.user("TECNICO", area);
        assertThat(upload(id, api.token(otherTechnician), "t.jpg", "image/jpeg", jpeg(100)).status()).isEqualTo(404);
        assign(id);

        assertThat(upload(id, technicianToken, "t.jpg", "image/jpeg", jpeg(100)).status()).isEqualTo(201);
        assertThat(upload(id, supervisorToken, "s.pdf", "application/pdf", pdf(100)).status()).isEqualTo(201);
    }

    @Test
    void autorEliminaConAuditoria() {
        String id = createRequest();
        assign(id);
        String attachment = upload(id, studentToken, "f.jpg", "image/jpeg", jpeg(100)).text("id");
        Path file = storedFile(attachment);
        assertThat(file).exists();

        Response deleted = api.send("DELETE", "/api/v1/requests/" + id + "/attachments/" + attachment, studentToken, null, Map.of());

        assertThat(deleted.status()).isEqualTo(204);
        assertThat(api.get("/api/v1/requests/" + id + "/attachments", studentToken).json().size()).isZero();
        Map<String, Object> row = data.jdbc().sql("SELECT eliminado_en, eliminado_por_id FROM adjuntos_solicitud WHERE id = :id")
                .param("id", Long.parseLong(attachment)).query().singleRow();
        assertThat(row.get("eliminado_en")).isNotNull();
        assertThat(((Number) row.get("eliminado_por_id")).longValue()).isEqualTo(student.id());
        assertThat(audits(id, "ELIMINAR_ADJUNTO")).isEqualTo(1);
        assertThat(file).doesNotExist();
    }

    @Test
    void soloElAutorElimina() {
        String id = createRequest();
        String attachment = upload(id, studentToken, "f.jpg", "image/jpeg", jpeg(100)).text("id");

        Response r = api.send("DELETE", "/api/v1/requests/" + id + "/attachments/" + attachment, supervisorToken, null, Map.of());

        assertThat(r.status()).isEqualTo(403);
        assertThat(active(id)).isEqualTo(1);
        assertThat(api.get("/api/v1/requests/" + id + "/attachments", supervisorToken).json().get(0).get("puedeEliminar")
                .asBoolean()).isFalse();
    }

    @Test
    void solicitudCerradaORechazadaNoAdmiteCambios() {
        String id = createRequest();
        String attachment = upload(id, studentToken, "f.jpg", "image/jpeg", jpeg(100)).text("id");
        assertThat(api.patch("/api/v1/requests/" + id + "/status", supervisorToken,
                Map.of("estado", "RECHAZADO", "motivo", "Otra sede")).status()).isEqualTo(200);

        Response up = upload(id, studentToken, "g.jpg", "image/jpeg", jpeg(100));
        assertThat(up.status()).isEqualTo(409);
        assertThat(up.codigo()).isEqualTo("SOLICITUD_FINALIZADA");
        Response del = api.send("DELETE", "/api/v1/requests/" + id + "/attachments/" + attachment, studentToken, null, Map.of());
        assertThat(del.status()).isEqualTo(409);
        assertThat(del.codigo()).isEqualTo("SOLICITUD_FINALIZADA");
        assertThat(api.download("/api/v1/requests/" + id + "/attachments/" + attachment + "/content", studentToken).statusCode())
                .isEqualTo(200);
    }

    @Test
    void nombreConRutaNoAfectaLaClave() throws Exception {
        String id = createRequest();

        Response r = upload(id, studentToken, "../../x.pdf", "application/pdf", pdf(100));

        assertThat(r.status()).isEqualTo(201);
        assertThat(r.text("nombre")).isEqualTo("x.pdf");
        String key = data.jdbc().sql("SELECT clave_objeto FROM adjuntos_solicitud WHERE id = :id")
                .param("id", Long.parseLong(r.text("id"))).query(String.class).single();
        assertThat(key).matches("^solicitudes/" + id + "/[0-9a-f-]{36}\\.pdf$");
        assertThat(Files.exists(storedFile(r.text("id")))).isTrue();
    }

    @Test
    void adminRecibe403() {
        String id = createRequest();
        String attachment = upload(id, studentToken, "f.jpg", "image/jpeg", jpeg(100)).text("id");
        String adminToken = api.token(data.user("ADMINISTRADOR", null));
        String base = "/api/v1/requests/" + id + "/attachments";

        assertThat(upload(id, adminToken, "a.jpg", "image/jpeg", jpeg(100)).status()).isEqualTo(403);
        assertThat(api.get(base, adminToken).status()).isEqualTo(403);
        assertThat(api.download(base + "/" + attachment + "/content", adminToken).statusCode()).isEqualTo(403);
        assertThat(api.send("DELETE", base + "/" + attachment, adminToken, null, Map.of()).status()).isEqualTo(403);
    }
}
