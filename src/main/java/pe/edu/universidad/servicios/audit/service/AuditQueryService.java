package pe.edu.universidad.servicios.audit.service;

import java.time.Instant;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import pe.edu.universidad.servicios.shared.dto.PageQuery;
import pe.edu.universidad.servicios.shared.dto.PageResponse;
import pe.edu.universidad.servicios.shared.persistence.Db;

/**
 * Consulta eventos de un recurso cuyo acceso ya autorizó el módulo dueño.
 */
@Service
public class AuditQueryService {

    private final JdbcClient jdbc;

    public AuditQueryService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public record AuditEvent(long id, String entidad, String accion, String campo, String valorAnterior,
            String valorNuevo, Long actorId, Instant ocurridoEn) {
    }

    public PageResponse<AuditEvent> forRequest(long solicitudId, PageQuery page) {
        List<AuditEvent> items = jdbc.sql("""
                SELECT id, entidad, accion, campo, valor_anterior, valor_nuevo, actor_id, ocurrido_en
                FROM auditoria_eventos
                WHERE solicitud_id = :id
                ORDER BY ocurrido_en, id
                LIMIT :limit OFFSET :offset
                """)
                .param("id", solicitudId).param("limit", page.size()).param("offset", page.offset())
                .query((rs, n) -> new AuditEvent(rs.getLong("id"), rs.getString("entidad"), rs.getString("accion"),
                        rs.getString("campo"), rs.getString("valor_anterior"), rs.getString("valor_nuevo"),
                        Db.nullableLong(rs, "actor_id"), Db.instant(rs, "ocurrido_en")))
                .list();
        long total = jdbc.sql("SELECT COUNT(*) FROM auditoria_eventos WHERE solicitud_id = :id")
                .param("id", solicitudId).query(Long.class).single();
        return new PageResponse<>(items, page.page(), page.size(), total);
    }
}
