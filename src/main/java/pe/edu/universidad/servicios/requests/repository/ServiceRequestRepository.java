package pe.edu.universidad.servicios.requests.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.core.simple.JdbcClient.StatementSpec;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import pe.edu.universidad.servicios.shared.dto.PageQuery;
import pe.edu.universidad.servicios.shared.persistence.Db;

@Repository
public class ServiceRequestRepository {

    private static final String SELECT = """
            SELECT s.id, s.codigo, s.solicitante_id, sol.nombre_completo AS solicitante_nombre, s.categoria_id,
                   c.nombre AS categoria_nombre, s.area_id, a.nombre AS area_nombre, s.prioridad_id,
                   p.codigo AS prioridad_codigo, p.nombre AS prioridad_nombre, e.codigo AS estado_codigo,
                   e.nombre AS estado_nombre, e.es_final, s.responsable_actual_id,
                   tec.nombre_completo AS responsable_nombre, s.ubicacion, s.descripcion, s.motivo_rechazo,
                   s.fecha_limite_sla, s.fecha_aviso_sla, s.resuelta_en, s.cerrada_en, s.version_fila, s.creado_en
            FROM solicitudes s
            JOIN usuarios sol ON sol.id = s.solicitante_id
            JOIN categorias c ON c.id = s.categoria_id
            JOIN areas a ON a.id = s.area_id
            JOIN prioridades p ON p.id = s.prioridad_id
            JOIN estados_solicitud e ON e.id = s.estado_id
            LEFT JOIN usuarios tec ON tec.id = s.responsable_actual_id
            """;

    private final JdbcClient jdbc;

    public ServiceRequestRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<RequestRow> findById(long id) {
        return jdbc.sql(SELECT + " WHERE s.id = :id").param("id", id).query(ServiceRequestRepository::map).optional();
    }

    /** Bloquea la solicitud para serializar asignaciones, prioridad y transiciones. */
    public Optional<RequestRow> lockById(long id) {
        jdbc.sql("SELECT id FROM solicitudes WHERE id = :id FOR UPDATE").param("id", id).query(Long.class).optional();
        return findById(id);
    }

    /** Código anual atómico por conexión (DISENIO-BD 7.7). */
    public String nextCode(int year) {
        jdbc.sql("""
                INSERT INTO secuencias_codigo_solicitud (anio, ultimo_valor)
                VALUES (:anio, LAST_INSERT_ID(1))
                ON DUPLICATE KEY UPDATE ultimo_valor = LAST_INSERT_ID(ultimo_valor + 1)
                """).param("anio", year).update();
        long value = jdbc.sql("SELECT LAST_INSERT_ID()").query(Long.class).single();
        return String.format("SOL-%d-%06d", year, value);
    }

    public long insert(String codigo, long solicitanteId, long categoriaId, long areaId, long prioridadId, long estadoId,
            Long politicaId, String ubicacion, String descripcion, Instant fechaLimite, Instant fechaAviso, Instant now) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.sql("""
                INSERT INTO solicitudes (codigo, solicitante_id, categoria_id, area_id, prioridad_id, estado_id,
                                         politica_sla_id, ubicacion, descripcion, fecha_limite_sla, fecha_aviso_sla,
                                         creado_en, actualizado_en)
                VALUES (:codigo, :solicitante, :categoria, :area, :prioridad, :estado, :politica, :ubicacion,
                        :descripcion, :limite, :aviso, :now, :now)
                """)
                .param("codigo", codigo).param("solicitante", solicitanteId).param("categoria", categoriaId)
                .param("area", areaId).param("prioridad", prioridadId).param("estado", estadoId)
                .param("politica", politicaId).param("ubicacion", ubicacion).param("descripcion", descripcion)
                .param("limite", Db.utc(fechaLimite)).param("aviso", Db.utc(fechaAviso)).param("now", Db.utc(now))
                .update(keys, "id");
        return keys.getKey().longValue();
    }

    /** Actualización con control de versión; devuelve false si otra escritura cambió la fila. */
    public boolean update(long id, int expectedVersion, long prioridadId, long estadoId, Long responsableId,
            String motivoRechazo, Instant resueltaEn, Instant cerradaEn, Instant now) {
        int rows = jdbc.sql("""
                UPDATE solicitudes
                SET prioridad_id = :prioridad, estado_id = :estado, responsable_actual_id = :responsable,
                    motivo_rechazo = :motivo, resuelta_en = :resuelta, cerrada_en = :cerrada,
                    version_fila = version_fila + 1, actualizado_en = :now
                WHERE id = :id AND version_fila = :version
                """)
                .param("prioridad", prioridadId).param("estado", estadoId).param("responsable", responsableId)
                .param("motivo", motivoRechazo).param("resuelta", Db.utc(resueltaEn)).param("cerrada", Db.utc(cerradaEn))
                .param("now", Db.utc(now)).param("id", id).param("version", expectedVersion)
                .update();
        return rows == 1;
    }

    public record ListFilter(Long solicitanteId, Long responsableId, Long areaId, String estado, Long categoriaId,
            Long prioridadId, boolean technicianOrder) {
    }

    /** El alcance se aplica antes de contar y paginar; el orden siempre desempata por id. */
    public List<RequestRow> list(ListFilter filter, PageQuery page) {
        String order = filter.technicianOrder()
                ? " ORDER BY p.nivel DESC, s.creado_en ASC, s.id ASC"
                : " ORDER BY s.creado_en DESC, s.id DESC";
        StatementSpec spec = jdbc.sql(SELECT + where() + order + " LIMIT :limit OFFSET :offset");
        return bind(spec, filter).param("limit", page.size()).param("offset", page.offset())
                .query(ServiceRequestRepository::map).list();
    }

    public long count(ListFilter filter) {
        StatementSpec spec = jdbc.sql("SELECT COUNT(*) FROM solicitudes s JOIN estados_solicitud e ON e.id = s.estado_id" + where());
        return bind(spec, filter).query(Long.class).single();
    }

    private static String where() {
        return """
                 WHERE (:solicitante IS NULL OR s.solicitante_id = :solicitante)
                   AND (:responsable IS NULL OR s.responsable_actual_id = :responsable)
                   AND (:area IS NULL OR s.area_id = :area)
                   AND (:estado IS NULL OR e.codigo = :estado)
                   AND (:categoria IS NULL OR s.categoria_id = :categoria)
                   AND (:prioridad IS NULL OR s.prioridad_id = :prioridad)
                """;
    }

    private static StatementSpec bind(StatementSpec spec, ListFilter f) {
        return spec.param("solicitante", f.solicitanteId()).param("responsable", f.responsableId())
                .param("area", f.areaId()).param("estado", f.estado()).param("categoria", f.categoriaId())
                .param("prioridad", f.prioridadId());
    }

    static RequestRow map(ResultSet rs, int n) throws SQLException {
        return new RequestRow(
                rs.getLong("id"), rs.getString("codigo"), rs.getLong("solicitante_id"), rs.getString("solicitante_nombre"),
                rs.getLong("categoria_id"), rs.getString("categoria_nombre"), rs.getLong("area_id"),
                rs.getString("area_nombre"), rs.getLong("prioridad_id"), rs.getString("prioridad_codigo"),
                rs.getString("prioridad_nombre"), rs.getString("estado_codigo"),
                rs.getString("estado_nombre"), rs.getBoolean("es_final"), Db.nullableLong(rs, "responsable_actual_id"),
                rs.getString("responsable_nombre"), rs.getString("ubicacion"), rs.getString("descripcion"),
                rs.getString("motivo_rechazo"), Db.instant(rs, "fecha_limite_sla"), Db.instant(rs, "fecha_aviso_sla"),
                Db.instant(rs, "resuelta_en"), Db.instant(rs, "cerrada_en"), rs.getInt("version_fila"),
                Db.instant(rs, "creado_en"));
    }
}
