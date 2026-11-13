package pe.edu.universidad.servicios.catalogs.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import pe.edu.universidad.servicios.catalogs.repository.CatalogRecords.Priority;
import pe.edu.universidad.servicios.shared.dto.PageQuery;

@Repository
public class PriorityRepository {

    private static final String SELECT = "SELECT id, codigo, nombre, nivel, color, activo FROM prioridades";

    private final JdbcClient jdbc;

    public PriorityRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Priority> findById(long id) {
        return jdbc.sql(SELECT + " WHERE id = :id").param("id", id).query(PriorityRepository::map).optional();
    }

    public List<Priority> page(PageQuery page) {
        return jdbc.sql(SELECT + " ORDER BY nivel, id LIMIT :limit OFFSET :offset")
                .param("limit", page.size()).param("offset", page.offset()).query(PriorityRepository::map).list();
    }

    public long count() {
        return jdbc.sql("SELECT COUNT(*) FROM prioridades").query(Long.class).single();
    }

    public long insert(String codigo, String nombre, int nivel, String color) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.sql("INSERT INTO prioridades (codigo, nombre, nivel, color, activo) VALUES (:codigo, :nombre, :nivel, :color, 1)")
                .param("codigo", codigo).param("nombre", nombre).param("nivel", nivel).param("color", color)
                .update(keys, "id");
        return keys.getKey().longValue();
    }

    public void update(long id, String nombre, int nivel, String color, boolean activo) {
        jdbc.sql("UPDATE prioridades SET nombre = :nombre, nivel = :nivel, color = :color, activo = :activo WHERE id = :id")
                .param("nombre", nombre).param("nivel", nivel).param("color", color).param("activo", activo).param("id", id)
                .update();
    }

    static Priority map(ResultSet rs, int n) throws SQLException {
        return new Priority(rs.getLong("id"), rs.getString("codigo"), rs.getString("nombre"), rs.getInt("nivel"),
                rs.getString("color"), rs.getBoolean("activo"));
    }
}
