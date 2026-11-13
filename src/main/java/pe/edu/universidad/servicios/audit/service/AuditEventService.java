package pe.edu.universidad.servicios.audit.service;

import java.time.Clock;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.universidad.servicios.shared.persistence.Db;
import pe.edu.universidad.servicios.shared.web.CorrelationIdFilter;

/**
 * Registra eventos de auditoría dentro de la transacción del caso de uso que escribe.
 * Si la escritura del evento falla, se revierte la operación de negocio completa.
 */
@Service
public class AuditEventService {

    private final JdbcClient jdbc;
    private final Clock clock;

    public AuditEventService(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public record AuditEntry(String entidad, long entidadId, Long solicitudId, String accion, String campo,
            String valorAnterior, String valorNuevo, Long actorId) {
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(AuditEntry entry) {
        String correlation = CorrelationIdFilter.current();
        jdbc.sql("""
                INSERT INTO auditoria_eventos (entidad, entidad_id, solicitud_id, accion, campo, valor_anterior,
                                               valor_nuevo, actor_id, ocurrido_en, id_correlacion)
                VALUES (:entidad, :entidadId, :solicitud, :accion, :campo, :anterior, :nuevo, :actor, :ahora, :correlacion)
                """)
                .param("entidad", entry.entidad()).param("entidadId", entry.entidadId())
                .param("solicitud", entry.solicitudId()).param("accion", entry.accion()).param("campo", entry.campo())
                .param("anterior", entry.valorAnterior()).param("nuevo", entry.valorNuevo())
                .param("actor", entry.actorId()).param("ahora", Db.utc(clock.instant()))
                .param("correlacion", correlation != null && correlation.length() <= 36 ? correlation : null)
                .update();
    }

    public void record(String entidad, long entidadId, Long solicitudId, String accion, String campo,
            String anterior, String nuevo, Long actorId) {
        record(new AuditEntry(entidad, entidadId, solicitudId, accion, campo, anterior, nuevo, actorId));
    }
}
