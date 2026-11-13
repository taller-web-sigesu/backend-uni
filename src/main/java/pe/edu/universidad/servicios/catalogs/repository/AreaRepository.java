package pe.edu.universidad.servicios.catalogs.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import pe.edu.universidad.servicios.catalogs.repository.CatalogRecords.Area;
import pe.edu.universidad.servicios.shared.dto.PageQuery;

@Repository
public class AreaRepository {

    private static final String SELECT = "SELECT id, codigo, nombre, descripcion, activo FROM areas";

    private final JdbcClient jdbc;

    public AreaRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Area> findById(long id) {
        return jdbc.sql(SELECT + " WHERE id = :id").param("id", id).query(AreaRepository::map).optional();
    }

    public List<Area> page(PageQuery page) {
        return jdbc.sql(SELECT + " ORDER BY id LIMIT :limit OFFSET :offset")
                .param("limit", page.size()).param("offset", page.offset()).query(AreaRepository::map).list();
    }

    public long count() {
        return jdbc.sql("SELECT COUNT(*) FROM areas").query(Long.class).single();
    }

    public long insert(String codigo, String nombre, String descripcion) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.sql("INSERT INTO areas (codigo, nombre, descripcion, activo) VALUES (:codigo, :nombre, :descripcion, 1)")
                .param("codigo", codigo).param("nombre", nombre).param("descripcion", descripcion)
                .update(keys, "id");
        return keys.getKey().longValue();
    }

    public void update(long id, String nombre, String descripcion, boolean activo) {
        jdbc.sql("UPDATE areas SET nombre = :nombre, descripcion = :descripcion, activo = :activo WHERE id = :id")
                .param("nombre", nombre).param("descripcion", descripcion).param("activo", activo).param("id", id)
                .update();
    }

    public long countActiveCategories(long areaId) {
        return jdbc.sql("SELECT COUNT(*) FROM categorias WHERE area_id = :id AND activo = 1")
                .param("id", areaId).query(Long.class).single();
    }

    static Area map(ResultSet rs, int n) throws SQLException {
        return new Area(rs.getLong("id"), rs.getString("codigo"), rs.getString("nombre"), rs.getString("descripcion"),
                rs.getBoolean("activo"));
    }
}
