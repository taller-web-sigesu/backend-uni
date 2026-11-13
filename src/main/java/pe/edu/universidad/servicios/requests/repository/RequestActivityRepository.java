package pe.edu.universidad.servicios.requests.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import pe.edu.universidad.servicios.shared.dto.PageQuery;
import pe.edu.universidad.servicios.shared.persistence.Db;

/**
 * Historial de estados, asignaciones, comentarios y encuestas de una solicitud.
 */
@Repository
public class RequestActivityRepository {

    private final JdbcClient jdbc;

    public RequestActivityRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    // Historial de estados

    public record HistoryRow(long id, String estadoAnterior, String estado, long actorId, String actorNombre,
            Instant fecha, String motivo) {
    }

    public void insertHistory(long solicitudId, Long origenId, long destinoId, long actorId, String motivo, Instant now) {
        jdbc.sql("""
                INSERT INTO historial_estados (solicitud_id, estado_origen_id, estado_destino_id, cambiado_por_id, motivo, cambiado_en)
                VALUES (:solicitud, :origen, :destino, :actor, :motivo, :now)
                """)
                .param("solicitud", solicitudId).param("origen", origenId).param("destino", destinoId)
                .param("actor", actorId).param("motivo", motivo).param("now", Db.utc(now))
                .update();
    }

    public List<HistoryRow> history(long solicitudId, PageQuery page) {
        return jdbc.sql("""
                SELECT h.id, o.codigo AS anterior, d.codigo AS estado, h.cambiado_por_id, u.nombre_completo,
                       h.cambiado_en, h.motivo
                FROM historial_estados h
                LEFT JOIN estados_solicitud o ON o.id = h.estado_origen_id
                JOIN estados_solicitud d ON d.id = h.estado_destino_id
                JOIN usuarios u ON u.id = h.cambiado_por_id
                WHERE h.solicitud_id = :id
                ORDER BY h.cambiado_en, h.id
                LIMIT :limit OFFSET :offset
                """)
                .param("id", solicitudId).param("limit", page.size()).param("offset", page.offset())
                .query((rs, n) -> new HistoryRow(rs.getLong("id"), rs.getString("anterior"), rs.getString("estado"),
                        rs.getLong("cambiado_por_id"), rs.getString("nombre_completo"), Db.instant(rs, "cambiado_en"),
                        rs.getString("motivo")))
                .list();
    }

    public long countHistory(long solicitudId) {
        return jdbc.sql("SELECT COUNT(*) FROM historial_estados WHERE solicitud_id = :id").param("id", solicitudId)
                .query(Long.class).single();
    }

    // Asignaciones

    public int finishActiveAssignment(long solicitudId, String motivo, Instant now) {
        return jdbc.sql("""
                UPDATE asignaciones SET finalizada_en = :now, motivo_fin = :motivo
                WHERE solicitud_id = :id AND finalizada_en IS NULL
                """)
                .param("now", Db.utc(now)).param("motivo", motivo).param("id", solicitudId)
                .update();
    }

    public long insertAssignment(long solicitudId, long tecnicoId, long asignadoPorId, Instant now) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.sql("""
                INSERT INTO asignaciones (solicitud_id, tecnico_id, asignado_por_id, asignada_en, creado_en)
                VALUES (:solicitud, :tecnico, :asignador, :now, :now)
                """)
                .param("solicitud", solicitudId).param("tecnico", tecnicoId).param("asignador", asignadoPorId)
                .param("now", Db.utc(now))
                .update(keys, "id");
        return keys.getKey().longValue();
    }

    public record Person(long id, String nombreCompleto) {
    }

    /** Último responsable registrado en asignaciones (proyección vw_ultimo_responsable). */
    public Optional<Person> lastTechnician(long solicitudId) {
        return jdbc.sql("SELECT tecnico_id, tecnico FROM vw_ultimo_responsable WHERE solicitud_id = :id")
                .param("id", solicitudId)
                .query((rs, n) -> new Person(rs.getLong("tecnico_id"), rs.getString("tecnico")))
                .optional();
    }

    // Comentarios

    public record CommentRow(long id, String contenido, long autorId, String autorNombre, Instant creadoEn) {
    }

    public long insertComment(long solicitudId, long autorId, String contenido, Instant now) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.sql("INSERT INTO comentarios (solicitud_id, autor_id, contenido, creado_en) VALUES (:solicitud, :autor, :contenido, :now)")
                .param("solicitud", solicitudId).param("autor", autorId).param("contenido", contenido).param("now", Db.utc(now))
                .update(keys, "id");
        return keys.getKey().longValue();
    }

    public List<CommentRow> comments(long solicitudId, PageQuery page) {
        return jdbc.sql("""
                SELECT c.id, c.contenido, c.autor_id, u.nombre_completo, c.creado_en
                FROM comentarios c JOIN usuarios u ON u.id = c.autor_id
                WHERE c.solicitud_id = :id
                ORDER BY c.creado_en, c.id
                LIMIT :limit OFFSET :offset
                """)
                .param("id", solicitudId).param("limit", page.size()).param("offset", page.offset())
                .query((rs, n) -> new CommentRow(rs.getLong("id"), rs.getString("contenido"), rs.getLong("autor_id"),
                        rs.getString("nombre_completo"), Db.instant(rs, "creado_en")))
                .list();
    }

    public long countComments(long solicitudId) {
        return jdbc.sql("SELECT COUNT(*) FROM comentarios WHERE solicitud_id = :id").param("id", solicitudId)
                .query(Long.class).single();
    }

    // Encuesta

    public boolean surveyExists(long solicitudId) {
        return jdbc.sql("SELECT COUNT(*) FROM encuestas_satisfaccion WHERE solicitud_id = :id").param("id", solicitudId)
                .query(Long.class).single() > 0;
    }

    public long insertSurvey(long solicitudId, long autorId, int calificacion, String comentario, Instant now) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.sql("""
                INSERT INTO encuestas_satisfaccion (solicitud_id, respondida_por_id, calificacion, comentario, respondida_en)
                VALUES (:solicitud, :autor, :calificacion, :comentario, :now)
                """)
                .param("solicitud", solicitudId).param("autor", autorId).param("calificacion", calificacion)
                .param("comentario", comentario).param("now", Db.utc(now))
                .update(keys, "id");
        return keys.getKey().longValue();
    }
}
