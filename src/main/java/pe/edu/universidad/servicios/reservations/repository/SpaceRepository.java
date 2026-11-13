package pe.edu.universidad.servicios.reservations.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
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
public class SpaceRepository {

    private static final String SELECT = """
            SELECT e.id, e.tipo_espacio_id, t.nombre AS tipo_nombre, t.activo AS tipo_activo, e.area_id, e.codigo,
                   e.nombre, e.descripcion, e.pabellon, e.piso, e.ubicacion_detallada, e.capacidad,
                   e.requiere_aprobacion, e.activo
            FROM espacios e
            JOIN tipos_espacio t ON t.id = e.tipo_espacio_id
            """;

    private final JdbcClient jdbc;

    public SpaceRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public record Space(long id, long tipoId, String tipoNombre, boolean tipoActivo, Long areaId, String codigo,
            String nombre, String descripcion, String pabellon, String piso, String ubicacionDetallada, int capacidad,
            boolean requiereAprobacion, boolean activo) {

        /** Disponible para nuevas reservas: espacio y tipo activos. */
        public boolean bookable() {
            return activo && tipoActivo;
        }
    }

    public record SpaceFilter(Long tipoEspacioId, String pabellon, Integer capacidadMinima) {
    }

    public Optional<Space> findById(long id) {
        return jdbc.sql(SELECT + " WHERE e.id = :id").param("id", id).query(SpaceRepository::map).optional();
    }

    /** Bloquea el espacio para serializar validación de solapamientos, altas y confirmaciones. */
    public Optional<Space> lockById(long id) {
        jdbc.sql("SELECT id FROM espacios WHERE id = :id FOR UPDATE").param("id", id).query(Long.class).optional();
        return findById(id);
    }

    public List<Space> page(PageQuery page) {
        return jdbc.sql(SELECT + " ORDER BY e.id LIMIT :limit OFFSET :offset")
                .param("limit", page.size()).param("offset", page.offset()).query(SpaceRepository::map).list();
    }

    public long count() {
        return jdbc.sql("SELECT COUNT(*) FROM espacios").query(Long.class).single();
    }

    public List<Space> searchBookable(SpaceFilter filter, PageQuery page) {
        return bind(jdbc.sql(SELECT + BOOKABLE + " ORDER BY e.nombre, e.id LIMIT :limit OFFSET :offset"), filter)
                .param("limit", page.size()).param("offset", page.offset()).query(SpaceRepository::map).list();
    }

    public long countBookable(SpaceFilter filter) {
        return bind(jdbc.sql("SELECT COUNT(*) FROM espacios e JOIN tipos_espacio t ON t.id = e.tipo_espacio_id" + BOOKABLE), filter)
                .query(Long.class).single();
    }

    private static final String BOOKABLE = """
             WHERE e.activo = 1 AND t.activo = 1
               AND (:tipo IS NULL OR e.tipo_espacio_id = :tipo)
               AND (:pabellon IS NULL OR e.pabellon = :pabellon)
               AND (:capacidad IS NULL OR e.capacidad >= :capacidad)
            """;

    private static StatementSpec bind(StatementSpec spec, SpaceFilter f) {
        return spec.param("tipo", f.tipoEspacioId()).param("pabellon", f.pabellon()).param("capacidad", f.capacidadMinima());
    }

    public long insert(long tipoId, Long areaId, String codigo, String nombre, String descripcion, String pabellon,
            String piso, String ubicacion, int capacidad) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.sql("""
                INSERT INTO espacios (tipo_espacio_id, area_id, codigo, nombre, descripcion, pabellon, piso,
                                      ubicacion_detallada, capacidad, requiere_aprobacion, activo)
                VALUES (:tipo, :area, :codigo, :nombre, :descripcion, :pabellon, :piso, :ubicacion, :capacidad, 1, 1)
                """)
                .param("tipo", tipoId).param("area", areaId).param("codigo", codigo).param("nombre", nombre)
                .param("descripcion", descripcion).param("pabellon", pabellon).param("piso", piso)
                .param("ubicacion", ubicacion).param("capacidad", capacidad)
                .update(keys, "id");
        return keys.getKey().longValue();
    }

    public void update(Space s) {
        jdbc.sql("""
                UPDATE espacios SET tipo_espacio_id = :tipo, area_id = :area, nombre = :nombre, descripcion = :descripcion,
                       pabellon = :pabellon, piso = :piso, ubicacion_detallada = :ubicacion, capacidad = :capacidad,
                       activo = :activo
                WHERE id = :id
                """)
                .param("tipo", s.tipoId()).param("area", s.areaId()).param("nombre", s.nombre())
                .param("descripcion", s.descripcion()).param("pabellon", s.pabellon()).param("piso", s.piso())
                .param("ubicacion", s.ubicacionDetallada()).param("capacidad", s.capacidad()).param("activo", s.activo())
                .param("id", s.id())
                .update();
    }

    static Space map(ResultSet rs, int n) throws SQLException {
        return new Space(rs.getLong("id"), rs.getLong("tipo_espacio_id"), rs.getString("tipo_nombre"),
                rs.getBoolean("tipo_activo"), Db.nullableLong(rs, "area_id"), rs.getString("codigo"), rs.getString("nombre"),
                rs.getString("descripcion"), rs.getString("pabellon"), rs.getString("piso"),
                rs.getString("ubicacion_detallada"), rs.getInt("capacidad"), rs.getBoolean("requiere_aprobacion"),
                rs.getBoolean("activo"));
    }
}
