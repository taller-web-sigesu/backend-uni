package pe.edu.universidad.servicios.dashboard.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.core.simple.JdbcClient.StatementSpec;
import org.springframework.stereotype.Repository;

import pe.edu.universidad.servicios.dashboard.domain.DashboardScope;
import pe.edu.universidad.servicios.shared.persistence.Db;

/**
 * Consultas de solo lectura de los nueve indicadores (DISENIO-BD sección 10) con un mismo alcance.
 */
@Repository
public class DashboardReadRepository {

    private static final String SCOPE = """
             WHERE s.creado_en >= :desde AND s.creado_en < :hasta AND (:area IS NULL OR s.area_id = :area)
            """;

    private final JdbcClient jdbc;

    public DashboardReadRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public record Totals(long registradas, long pendientes, long atendidas, double promedioHorasAtencion,
            double porcentajeResueltas, long vencidas, long porVencer) {
    }

    public record Group(Long id, String nombre, long total) {
    }

    public record RequestExportRow(String codigo, String categoria, String area, String estado, String prioridad,
            String responsable, Instant creadoEn) {
    }

    public Totals totals(DashboardScope scope, Instant now) {
        return bind(jdbc.sql("""
                SELECT COUNT(*) AS registradas,
                       COALESCE(SUM(e.codigo IN ('NUEVO', 'ASIGNADO', 'EN_PROCESO')), 0) AS pendientes,
                       COALESCE(SUM(e.codigo IN ('RESUELTO', 'CERRADO')), 0) AS atendidas,
                       COALESCE(ROUND(AVG(CASE WHEN s.resuelta_en IS NOT NULL
                                          THEN TIMESTAMPDIFF(MINUTE, s.creado_en, s.resuelta_en) END) / 60, 2), 0) AS promedio_horas,
                       COALESCE(ROUND(100.0 * SUM(e.codigo IN ('RESUELTO', 'CERRADO')) / NULLIF(COUNT(*), 0), 2), 0) AS porcentaje,
                       COALESCE(SUM(e.es_final = 0 AND s.fecha_limite_sla IS NOT NULL AND :ahora > s.fecha_limite_sla), 0) AS vencidas,
                       COALESCE(SUM(e.es_final = 0 AND s.fecha_limite_sla IS NOT NULL AND :ahora <= s.fecha_limite_sla
                                    AND s.fecha_aviso_sla IS NOT NULL AND :ahora >= s.fecha_aviso_sla), 0) AS por_vencer
                FROM solicitudes s
                JOIN estados_solicitud e ON e.id = s.estado_id
                """ + SCOPE), scope)
                .param("ahora", Db.utc(now))
                .query((rs, n) -> new Totals(rs.getLong("registradas"), rs.getLong("pendientes"), rs.getLong("atendidas"),
                        rs.getDouble("promedio_horas"), rs.getDouble("porcentaje"), rs.getLong("vencidas"),
                        rs.getLong("por_vencer")))
                .single();
    }

    public List<Group> byCategory(DashboardScope scope) {
        return bind(jdbc.sql("""
                SELECT c.id, c.nombre, COUNT(*) AS total
                FROM solicitudes s JOIN categorias c ON c.id = s.categoria_id
                """ + SCOPE + " GROUP BY c.id, c.nombre ORDER BY total DESC, c.nombre, c.id"), scope)
                .query((rs, n) -> new Group(rs.getLong("id"), rs.getString("nombre"), rs.getLong("total"))).list();
    }

    public List<Group> byPriority(DashboardScope scope) {
        return bind(jdbc.sql("""
                SELECT p.id, p.nombre, COUNT(*) AS total
                FROM solicitudes s JOIN prioridades p ON p.id = s.prioridad_id
                """ + SCOPE + " GROUP BY p.id, p.nombre, p.nivel ORDER BY p.nivel DESC, p.id"), scope)
                .query((rs, n) -> new Group(rs.getLong("id"), rs.getString("nombre"), rs.getLong("total"))).list();
    }

    /** Responsable actual o, en solicitudes terminales, el último técnico asignado. */
    public List<Group> byResponsible(DashboardScope scope) {
        return bind(jdbc.sql("""
                SELECT r.responsable_id AS id, u.nombre_completo AS nombre, COUNT(*) AS total
                FROM (
                    SELECT COALESCE(s.responsable_actual_id, CASE WHEN e.es_final = 1 THEN ur.tecnico_id END) AS responsable_id
                    FROM solicitudes s
                    JOIN estados_solicitud e ON e.id = s.estado_id
                    LEFT JOIN vw_ultimo_responsable ur ON ur.solicitud_id = s.id
                """ + SCOPE + """
                ) r
                LEFT JOIN usuarios u ON u.id = r.responsable_id
                GROUP BY r.responsable_id, u.nombre_completo
                ORDER BY total DESC, nombre, id
                """), scope)
                .query((rs, n) -> new Group(Db.nullableLong(rs, "id"), rs.getString("nombre"), rs.getLong("total"))).list();
    }

    public List<RequestExportRow> requestsForExport(DashboardScope scope) {
        return bind(jdbc.sql("""
                SELECT s.codigo, c.nombre AS categoria, a.nombre AS area, e.codigo AS estado, p.nombre AS prioridad,
                       COALESCE(t.nombre_completo, CASE WHEN e.es_final = 1 THEN ur.tecnico END) AS responsable, s.creado_en
                FROM solicitudes s
                JOIN categorias c ON c.id = s.categoria_id
                JOIN areas a ON a.id = s.area_id
                JOIN estados_solicitud e ON e.id = s.estado_id
                JOIN prioridades p ON p.id = s.prioridad_id
                LEFT JOIN usuarios t ON t.id = s.responsable_actual_id
                LEFT JOIN vw_ultimo_responsable ur ON ur.solicitud_id = s.id
                """ + SCOPE + " ORDER BY s.creado_en, s.id"), scope)
                .query((rs, n) -> new RequestExportRow(rs.getString("codigo"), rs.getString("categoria"), rs.getString("area"),
                        rs.getString("estado"), rs.getString("prioridad"), rs.getString("responsable"),
                        Db.instant(rs, "creado_en")))
                .list();
    }

    private static StatementSpec bind(StatementSpec spec, DashboardScope scope) {
        return spec.param("desde", Db.utc(scope.desde())).param("hasta", Db.utc(scope.hasta())).param("area", scope.areaId());
    }
}
