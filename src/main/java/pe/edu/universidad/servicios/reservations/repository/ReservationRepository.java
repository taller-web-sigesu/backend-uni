package pe.edu.universidad.servicios.reservations.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
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
public class ReservationRepository {

    /** Estados que ocupan la franja. */
    public static final List<String> ACTIVE_STATES = List.of("PENDIENTE", "CONFIRMADA");

    private static final String SELECT = """
            SELECT r.id, r.codigo, r.espacio_id, esp.nombre AS espacio_nombre, esp.area_id AS espacio_area_id,
                   r.solicitante_id, er.codigo AS estado, r.fecha, r.hora_inicio, r.hora_fin, r.motivo, r.num_asistentes,
                   r.motivo_rechazo, r.aprobada_por_id, r.cancelada_por_id, r.version_fila, r.creado_en
            FROM reservas r
            JOIN espacios esp ON esp.id = r.espacio_id
            JOIN estados_reserva er ON er.id = r.estado_id
            """;

    private final JdbcClient jdbc;

    public ReservationRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public record ReservationRow(long id, String codigo, long espacioId, String espacioNombre, Long espacioAreaId,
            long solicitanteId, String estado, LocalDate fecha, LocalTime horaInicio, LocalTime horaFin, String motivo,
            Integer numAsistentes, String motivoRechazo, Long aprobadaPorId, Long canceladaPorId, int version,
            Instant creadoEn) {
    }

    public record TimeRange(LocalTime inicio, LocalTime fin) {
    }

    public Optional<ReservationRow> findById(long id) {
        return jdbc.sql(SELECT + " WHERE r.id = :id").param("id", id).query(ReservationRepository::map).optional();
    }

    public Optional<ReservationRow> lockById(long id) {
        jdbc.sql("SELECT id FROM reservas WHERE id = :id FOR UPDATE").param("id", id).query(Long.class).optional();
        return findById(id);
    }

    public long stateId(String codigo) {
        return jdbc.sql("SELECT id FROM estados_reserva WHERE codigo = :c").param("c", codigo).query(Long.class).single();
    }

    public String nextCode(int year) {
        jdbc.sql("""
                INSERT INTO secuencias_codigo_reserva (anio, ultimo_valor)
                VALUES (:anio, LAST_INSERT_ID(1))
                ON DUPLICATE KEY UPDATE ultimo_valor = LAST_INSERT_ID(ultimo_valor + 1)
                """).param("anio", year).update();
        long value = jdbc.sql("SELECT LAST_INSERT_ID()").query(Long.class).single();
        return String.format("RES-%d-%06d", year, value);
    }

    /** Cruce con inicio inclusivo y fin exclusivo: inicio < finExistente y fin > inicioExistente. */
    public boolean overlaps(long espacioId, LocalDate fecha, LocalTime inicio, LocalTime fin, Long excludeId) {
        return jdbc.sql("""
                SELECT COUNT(*) FROM reservas r JOIN estados_reserva er ON er.id = r.estado_id
                WHERE r.espacio_id = :espacio AND r.fecha = :fecha AND er.codigo IN ('PENDIENTE', 'CONFIRMADA')
                  AND r.hora_inicio < :fin AND r.hora_fin > :inicio AND (:excluir IS NULL OR r.id <> :excluir)
                """)
                .param("espacio", espacioId).param("fecha", fecha).param("inicio", inicio).param("fin", fin)
                .param("excluir", excludeId)
                .query(Long.class).single() > 0;
    }

    public long countActiveForRequester(long solicitanteId, LocalDate fecha) {
        return jdbc.sql("""
                SELECT COUNT(*) FROM reservas r JOIN estados_reserva er ON er.id = r.estado_id
                WHERE r.solicitante_id = :solicitante AND r.fecha = :fecha AND er.codigo IN ('PENDIENTE', 'CONFIRMADA')
                """)
                .param("solicitante", solicitanteId).param("fecha", fecha).query(Long.class).single();
    }

    public List<TimeRange> occupied(long espacioId, LocalDate fecha) {
        return jdbc.sql("""
                SELECT r.hora_inicio, r.hora_fin FROM reservas r JOIN estados_reserva er ON er.id = r.estado_id
                WHERE r.espacio_id = :espacio AND r.fecha = :fecha AND er.codigo IN ('PENDIENTE', 'CONFIRMADA')
                ORDER BY r.hora_inicio, r.hora_fin
                """)
                .param("espacio", espacioId).param("fecha", fecha)
                .query((rs, n) -> new TimeRange(Db.time(rs, "hora_inicio"), Db.time(rs, "hora_fin")))
                .list();
    }

    public long insert(String codigo, long espacioId, long solicitanteId, long estadoId, LocalDate fecha, LocalTime inicio,
            LocalTime fin, String motivo, Integer asistentes, Instant now) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.sql("""
                INSERT INTO reservas (codigo, espacio_id, solicitante_id, estado_id, fecha, hora_inicio, hora_fin, motivo,
                                      num_asistentes, creado_en, actualizado_en)
                VALUES (:codigo, :espacio, :solicitante, :estado, :fecha, :inicio, :fin, :motivo, :asistentes, :now, :now)
                """)
                .param("codigo", codigo).param("espacio", espacioId).param("solicitante", solicitanteId)
                .param("estado", estadoId).param("fecha", fecha).param("inicio", inicio).param("fin", fin)
                .param("motivo", motivo).param("asistentes", asistentes).param("now", Db.utc(now))
                .update(keys, "id");
        return keys.getKey().longValue();
    }

    public boolean confirm(long id, int version, long estadoId, long aprobadorId, Instant now) {
        return jdbc.sql("""
                UPDATE reservas SET estado_id = :estado, aprobada_por_id = :aprobador, aprobada_en = :now,
                       version_fila = version_fila + 1, actualizado_en = :now
                WHERE id = :id AND version_fila = :version
                """)
                .param("estado", estadoId).param("aprobador", aprobadorId).param("now", Db.utc(now))
                .param("id", id).param("version", version).update() == 1;
    }

    public boolean reject(long id, int version, long estadoId, String motivo, Instant now) {
        return jdbc.sql("""
                UPDATE reservas SET estado_id = :estado, motivo_rechazo = :motivo, version_fila = version_fila + 1,
                       actualizado_en = :now
                WHERE id = :id AND version_fila = :version
                """)
                .param("estado", estadoId).param("motivo", motivo).param("now", Db.utc(now))
                .param("id", id).param("version", version).update() == 1;
    }

    public boolean cancel(long id, int version, long estadoId, long actorId, Instant now) {
        return jdbc.sql("""
                UPDATE reservas SET estado_id = :estado, cancelada_por_id = :actor, cancelada_en = :now,
                       version_fila = version_fila + 1, actualizado_en = :now
                WHERE id = :id AND version_fila = :version
                """)
                .param("estado", estadoId).param("actor", actorId).param("now", Db.utc(now))
                .param("id", id).param("version", version).update() == 1;
    }

    public record ListFilter(Long solicitanteId, boolean management, Long areaId, String estado, LocalDate desde,
            LocalDate hasta) {
    }

    public List<ReservationRow> list(ListFilter f, PageQuery page) {
        String order = f.management()
                ? " ORDER BY r.fecha, r.hora_inicio, r.id"
                : " ORDER BY r.fecha DESC, r.hora_inicio DESC, r.id DESC";
        return bind(jdbc.sql(SELECT + WHERE + order + " LIMIT :limit OFFSET :offset"), f)
                .param("limit", page.size()).param("offset", page.offset())
                .query(ReservationRepository::map).list();
    }

    public long count(ListFilter f) {
        return bind(jdbc.sql("""
                SELECT COUNT(*) FROM reservas r JOIN espacios esp ON esp.id = r.espacio_id
                JOIN estados_reserva er ON er.id = r.estado_id
                """ + WHERE), f).query(Long.class).single();
    }

    private static final String WHERE = """
             WHERE (:solicitante IS NULL OR r.solicitante_id = :solicitante)
               AND (:gestion = 0 OR :area IS NULL OR esp.area_id = :area)
               AND (:estado IS NULL OR er.codigo = :estado)
               AND (:desde IS NULL OR r.fecha >= :desde)
               AND (:hasta IS NULL OR r.fecha <= :hasta)
            """;

    private static StatementSpec bind(StatementSpec spec, ListFilter f) {
        return spec.param("solicitante", f.solicitanteId()).param("gestion", f.management() ? 1 : 0)
                .param("area", f.areaId()).param("estado", f.estado()).param("desde", f.desde()).param("hasta", f.hasta());
    }

    // Historial

    public record HistoryRow(String estadoAnterior, String estado, long actorId, Instant fecha, String motivo) {
    }

    public void insertHistory(long reservaId, Long origenId, long destinoId, long actorId, String motivo, Instant now) {
        jdbc.sql("""
                INSERT INTO historial_estados_reserva (reserva_id, estado_origen_id, estado_destino_id, cambiado_por_id, motivo, cambiado_en)
                VALUES (:reserva, :origen, :destino, :actor, :motivo, :now)
                """)
                .param("reserva", reservaId).param("origen", origenId).param("destino", destinoId)
                .param("actor", actorId).param("motivo", motivo).param("now", Db.utc(now))
                .update();
    }

    public List<HistoryRow> history(long reservaId, PageQuery page) {
        return jdbc.sql("""
                SELECT o.codigo AS anterior, d.codigo AS estado, h.cambiado_por_id, h.cambiado_en, h.motivo
                FROM historial_estados_reserva h
                LEFT JOIN estados_reserva o ON o.id = h.estado_origen_id
                JOIN estados_reserva d ON d.id = h.estado_destino_id
                WHERE h.reserva_id = :id
                ORDER BY h.cambiado_en, h.id
                LIMIT :limit OFFSET :offset
                """)
                .param("id", reservaId).param("limit", page.size()).param("offset", page.offset())
                .query((rs, n) -> new HistoryRow(rs.getString("anterior"), rs.getString("estado"), rs.getLong("cambiado_por_id"),
                        Db.instant(rs, "cambiado_en"), rs.getString("motivo")))
                .list();
    }

    public long countHistory(long reservaId) {
        return jdbc.sql("SELECT COUNT(*) FROM historial_estados_reserva WHERE reserva_id = :id").param("id", reservaId)
                .query(Long.class).single();
    }

    static ReservationRow map(ResultSet rs, int n) throws SQLException {
        return new ReservationRow(rs.getLong("id"), rs.getString("codigo"), rs.getLong("espacio_id"),
                rs.getString("espacio_nombre"), Db.nullableLong(rs, "espacio_area_id"), rs.getLong("solicitante_id"),
                rs.getString("estado"), Db.date(rs, "fecha"), Db.time(rs, "hora_inicio"), Db.time(rs, "hora_fin"),
                rs.getString("motivo"), Db.nullableInt(rs, "num_asistentes"), rs.getString("motivo_rechazo"),
                Db.nullableLong(rs, "aprobada_por_id"), Db.nullableLong(rs, "cancelada_por_id"), rs.getInt("version_fila"),
                Db.instant(rs, "creado_en"));
    }
}
