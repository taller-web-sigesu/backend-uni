package pe.edu.universidad.servicios.sla.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import pe.edu.universidad.servicios.shared.dto.PageQuery;
import pe.edu.universidad.servicios.shared.persistence.Db;

@Repository
public class SlaPolicyRepository {

    private static final String SELECT = """
            SELECT id, categoria_id, prioridad_id, horas_objetivo, horas_aviso, vigente_desde, vigente_hasta, activo
            FROM politicas_sla
            """;

    private final JdbcClient jdbc;

    public SlaPolicyRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public record SlaPolicy(long id, Long categoriaId, Long prioridadId, int horasObjetivo, int horasAviso,
            Instant vigenteDesde, Instant vigenteHasta, boolean activo) {

        public boolean isEffectiveAt(Instant instant) {
            return activo && !vigenteDesde.isAfter(instant) && (vigenteHasta == null || vigenteHasta.isAfter(instant));
        }
    }

    public Optional<SlaPolicy> findById(long id) {
        return jdbc.sql(SELECT + " WHERE id = :id").param("id", id).query(SlaPolicyRepository::map).optional();
    }

    public List<SlaPolicy> page(PageQuery page) {
        return jdbc.sql(SELECT + " ORDER BY id LIMIT :limit OFFSET :offset")
                .param("limit", page.size()).param("offset", page.offset()).query(SlaPolicyRepository::map).list();
    }

    public long count() {
        return jdbc.sql("SELECT COUNT(*) FROM politicas_sla").query(Long.class).single();
    }

    /** Políticas activas del mismo alcance (categoría o prioridad), bloqueadas para validar superposición. */
    public List<SlaPolicy> lockActiveSameScope(Long categoriaId, Long prioridadId) {
        String where = categoriaId != null ? " WHERE categoria_id = :scope" : " WHERE prioridad_id = :scope";
        return jdbc.sql(SELECT + where + " AND activo = 1 FOR UPDATE")
                .param("scope", categoriaId != null ? categoriaId : prioridadId)
                .query(SlaPolicyRepository::map).list();
    }

    public Optional<SlaPolicy> effectiveForCategory(long categoriaId, Instant at) {
        return effective("categoria_id", categoriaId, at);
    }

    public Optional<SlaPolicy> effectiveForPriority(long prioridadId, Instant at) {
        return effective("prioridad_id", prioridadId, at);
    }

    private Optional<SlaPolicy> effective(String column, long value, Instant at) {
        return jdbc.sql(SELECT + " WHERE " + column + " = :value AND activo = 1 AND vigente_desde <= :at"
                        + " AND (vigente_hasta IS NULL OR vigente_hasta > :at) ORDER BY vigente_desde DESC, id DESC LIMIT 1")
                .param("value", value).param("at", Db.utc(at))
                .query(SlaPolicyRepository::map).optional();
    }

    public boolean anyActive() {
        return jdbc.sql("SELECT COUNT(*) FROM politicas_sla WHERE activo = 1").query(Long.class).single() > 0;
    }

    public long insert(Long categoriaId, Long prioridadId, int horasObjetivo, int horasAviso, Instant desde, Instant hasta) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.sql("""
                INSERT INTO politicas_sla (prioridad_id, categoria_id, horas_objetivo, horas_aviso, vigente_desde, vigente_hasta, activo)
                VALUES (:prioridad, :categoria, :objetivo, :aviso, :desde, :hasta, 1)
                """)
                .param("prioridad", prioridadId).param("categoria", categoriaId).param("objetivo", horasObjetivo)
                .param("aviso", horasAviso).param("desde", Db.utc(desde)).param("hasta", Db.utc(hasta))
                .update(keys, "id");
        return keys.getKey().longValue();
    }

    public void update(long id, int horasObjetivo, int horasAviso, Instant desde, Instant hasta, boolean activo) {
        jdbc.sql("""
                UPDATE politicas_sla SET horas_objetivo = :objetivo, horas_aviso = :aviso, vigente_desde = :desde,
                       vigente_hasta = :hasta, activo = :activo
                WHERE id = :id
                """)
                .param("objetivo", horasObjetivo).param("aviso", horasAviso).param("desde", Db.utc(desde))
                .param("hasta", Db.utc(hasta)).param("activo", activo).param("id", id)
                .update();
    }

    static SlaPolicy map(ResultSet rs, int n) throws SQLException {
        return new SlaPolicy(rs.getLong("id"), Db.nullableLong(rs, "categoria_id"), Db.nullableLong(rs, "prioridad_id"),
                rs.getInt("horas_objetivo"), rs.getInt("horas_aviso"), Db.instant(rs, "vigente_desde"),
                Db.instant(rs, "vigente_hasta"), rs.getBoolean("activo"));
    }
}
