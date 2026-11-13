package pe.edu.universidad.servicios.catalogs.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import pe.edu.universidad.servicios.catalogs.repository.CatalogRecords.Category;
import pe.edu.universidad.servicios.shared.dto.PageQuery;

@Repository
public class CategoryRepository {

    private static final String SELECT = """
            SELECT c.id, c.codigo, c.nombre, c.descripcion, c.area_id, c.prioridad_predeterminada_id, c.activo
            FROM categorias c
            """;
    private static final String AVAILABLE = """
             JOIN areas a ON a.id = c.area_id
             JOIN prioridades p ON p.id = c.prioridad_predeterminada_id
             WHERE c.activo = 1 AND a.activo = 1 AND p.activo = 1
            """;

    private final JdbcClient jdbc;

    public CategoryRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Category> findById(long id) {
        return jdbc.sql(SELECT + " WHERE c.id = :id").param("id", id).query(CategoryRepository::map).optional();
    }

    public List<Category> page(PageQuery page) {
        return jdbc.sql(SELECT + " ORDER BY c.id LIMIT :limit OFFSET :offset")
                .param("limit", page.size()).param("offset", page.offset()).query(CategoryRepository::map).list();
    }

    public long count() {
        return jdbc.sql("SELECT COUNT(*) FROM categorias").query(Long.class).single();
    }

    /** Categorías habilitadas para registrar: categoría, área y prioridad predeterminada activas. */
    public List<Category> availablePage(PageQuery page) {
        return jdbc.sql(SELECT + AVAILABLE + " ORDER BY c.nombre, c.id LIMIT :limit OFFSET :offset")
                .param("limit", page.size()).param("offset", page.offset()).query(CategoryRepository::map).list();
    }

    public long countAvailable() {
        return jdbc.sql("SELECT COUNT(*) FROM categorias c" + AVAILABLE).query(Long.class).single();
    }

    public Optional<Category> findAvailable(long id) {
        return jdbc.sql(SELECT + AVAILABLE + " AND c.id = :id").param("id", id).query(CategoryRepository::map).optional();
    }

    public long insert(String codigo, String nombre, String descripcion, long areaId, long prioridadId) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.sql("""
                INSERT INTO categorias (area_id, prioridad_predeterminada_id, codigo, nombre, descripcion, activo)
                VALUES (:area, :prioridad, :codigo, :nombre, :descripcion, 1)
                """)
                .param("area", areaId).param("prioridad", prioridadId).param("codigo", codigo).param("nombre", nombre)
                .param("descripcion", descripcion)
                .update(keys, "id");
        return keys.getKey().longValue();
    }

    public void update(long id, String nombre, String descripcion, long areaId, long prioridadId, boolean activo) {
        jdbc.sql("""
                UPDATE categorias SET nombre = :nombre, descripcion = :descripcion, area_id = :area,
                       prioridad_predeterminada_id = :prioridad, activo = :activo
                WHERE id = :id
                """)
                .param("nombre", nombre).param("descripcion", descripcion).param("area", areaId)
                .param("prioridad", prioridadId).param("activo", activo).param("id", id)
                .update();
    }

    public long countActiveUsingPriority(long prioridadId) {
        return jdbc.sql("SELECT COUNT(*) FROM categorias WHERE prioridad_predeterminada_id = :id AND activo = 1")
                .param("id", prioridadId).query(Long.class).single();
    }

    static Category map(ResultSet rs, int n) throws SQLException {
        return new Category(rs.getLong("id"), rs.getString("codigo"), rs.getString("nombre"), rs.getString("descripcion"),
                rs.getLong("area_id"), rs.getLong("prioridad_predeterminada_id"), rs.getBoolean("activo"));
    }
}
