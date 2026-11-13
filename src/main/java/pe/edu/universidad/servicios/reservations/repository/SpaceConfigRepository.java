package pe.edu.universidad.servicios.reservations.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import pe.edu.universidad.servicios.shared.dto.PageQuery;
import pe.edu.universidad.servicios.shared.persistence.Db;

/**
 * Tipos de espacio, horarios semanales y política global de reservas.
 */
@Repository
public class SpaceConfigRepository {

    private final JdbcClient jdbc;

    public SpaceConfigRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    // Tipos de espacio

    public record SpaceType(long id, String codigo, String nombre, String descripcion, boolean activo) {
    }

    private static final String TYPE_SELECT = "SELECT id, codigo, nombre, descripcion, activo FROM tipos_espacio";

    public Optional<SpaceType> findType(long id) {
        return jdbc.sql(TYPE_SELECT + " WHERE id = :id").param("id", id).query(SpaceConfigRepository::mapType).optional();
    }

    public List<SpaceType> typePage(PageQuery page) {
        return jdbc.sql(TYPE_SELECT + " ORDER BY id LIMIT :limit OFFSET :offset")
                .param("limit", page.size()).param("offset", page.offset()).query(SpaceConfigRepository::mapType).list();
    }

    public long countTypes() {
        return jdbc.sql("SELECT COUNT(*) FROM tipos_espacio").query(Long.class).single();
    }

    public long insertType(String codigo, String nombre, String descripcion) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.sql("INSERT INTO tipos_espacio (codigo, nombre, descripcion, activo) VALUES (:codigo, :nombre, :descripcion, 1)")
                .param("codigo", codigo).param("nombre", nombre).param("descripcion", descripcion)
                .update(keys, "id");
        return keys.getKey().longValue();
    }

    public void updateType(long id, String nombre, String descripcion, boolean activo) {
        jdbc.sql("UPDATE tipos_espacio SET nombre = :nombre, descripcion = :descripcion, activo = :activo WHERE id = :id")
                .param("nombre", nombre).param("descripcion", descripcion).param("activo", activo).param("id", id).update();
    }

    private static SpaceType mapType(ResultSet rs, int n) throws SQLException {
        return new SpaceType(rs.getLong("id"), rs.getString("codigo"), rs.getString("nombre"), rs.getString("descripcion"),
                rs.getBoolean("activo"));
    }

    // Horarios semanales (una franja activa por espacio y día)

    public record ScheduleSlot(int diaSemana, LocalTime horaApertura, LocalTime horaCierre) {
    }

    public List<ScheduleSlot> activeSchedule(long espacioId) {
        return jdbc.sql("""
                SELECT dia_semana, hora_apertura, hora_cierre FROM horarios_espacio
                WHERE espacio_id = :id AND activo = 1 ORDER BY dia_semana
                """)
                .param("id", espacioId)
                .query((rs, n) -> new ScheduleSlot(rs.getInt("dia_semana"), Db.time(rs, "hora_apertura"), Db.time(rs, "hora_cierre")))
                .list();
    }

    public Optional<ScheduleSlot> activeSlot(long espacioId, int diaSemana) {
        return jdbc.sql("""
                SELECT dia_semana, hora_apertura, hora_cierre FROM horarios_espacio
                WHERE espacio_id = :id AND dia_semana = :dia AND activo = 1
                """)
                .param("id", espacioId).param("dia", diaSemana)
                .query((rs, n) -> new ScheduleSlot(rs.getInt("dia_semana"), Db.time(rs, "hora_apertura"), Db.time(rs, "hora_cierre")))
                .optional();
    }

    public void upsertSlot(long espacioId, ScheduleSlot slot) {
        jdbc.sql("""
                INSERT INTO horarios_espacio (espacio_id, dia_semana, hora_apertura, hora_cierre, activo)
                VALUES (:id, :dia, :apertura, :cierre, 1)
                ON DUPLICATE KEY UPDATE hora_apertura = VALUES(hora_apertura), hora_cierre = VALUES(hora_cierre), activo = 1
                """)
                .param("id", espacioId).param("dia", slot.diaSemana()).param("apertura", slot.horaApertura())
                .param("cierre", slot.horaCierre())
                .update();
    }

    public void closeDay(long espacioId, int diaSemana) {
        jdbc.sql("UPDATE horarios_espacio SET activo = 0 WHERE espacio_id = :id AND dia_semana = :dia")
                .param("id", espacioId).param("dia", diaSemana).update();
    }

    // Política global

    public record ReservationPolicy(long id, int duracionMinimaMinutos, int duracionMaximaMinutos,
            int horasAnticipacionMinima, int diasAnticipacionMaxima, int maxReservasActivasPorDia) {
    }

    public Optional<ReservationPolicy> currentPolicy(Instant now) {
        return jdbc.sql("""
                SELECT id, duracion_minima_minutos, duracion_maxima_minutos, horas_anticipacion_minima,
                       dias_anticipacion_maxima, max_reservas_activas_por_dia
                FROM politicas_reserva
                WHERE activo = 1 AND vigente_desde <= :now AND (vigente_hasta IS NULL OR vigente_hasta > :now)
                ORDER BY vigente_desde DESC, id DESC LIMIT 1
                """)
                .param("now", Db.utc(now))
                .query((rs, n) -> new ReservationPolicy(rs.getLong("id"), rs.getInt("duracion_minima_minutos"),
                        rs.getInt("duracion_maxima_minutos"), rs.getInt("horas_anticipacion_minima"),
                        rs.getInt("dias_anticipacion_maxima"), rs.getInt("max_reservas_activas_por_dia")))
                .optional();
    }

    /** Cierra la vigencia de las políticas activas y registra la nueva desde el instante indicado. */
    public long replacePolicy(int min, int max, int horasMin, int diasMax, int limite, Instant now) {
        jdbc.sql("""
                UPDATE politicas_reserva
                SET activo = 0, vigente_hasta = GREATEST(:now, vigente_desde + INTERVAL 1 MICROSECOND)
                WHERE activo = 1
                """).param("now", Db.utc(now)).update();
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.sql("""
                INSERT INTO politicas_reserva (duracion_minima_minutos, duracion_maxima_minutos, dias_anticipacion_maxima,
                                               horas_anticipacion_minima, max_reservas_activas_por_dia, vigente_desde, activo)
                VALUES (:min, :max, :dias, :horas, :limite, :now, 1)
                """)
                .param("min", min).param("max", max).param("dias", diasMax).param("horas", horasMin).param("limite", limite)
                .param("now", Db.utc(now))
                .update(keys, "id");
        return keys.getKey().longValue();
    }
}
