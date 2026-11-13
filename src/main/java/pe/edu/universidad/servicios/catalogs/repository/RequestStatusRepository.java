package pe.edu.universidad.servicios.catalogs.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import pe.edu.universidad.servicios.catalogs.repository.CatalogRecords.RequestStatus;
import pe.edu.universidad.servicios.catalogs.repository.CatalogRecords.Transition;

@Repository
public class RequestStatusRepository {

    private static final String SELECT = "SELECT id, codigo, nombre, es_final, orden FROM estados_solicitud";

    private final JdbcClient jdbc;

    public RequestStatusRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<RequestStatus> findAll() {
        return jdbc.sql(SELECT + " ORDER BY orden, id").query(RequestStatusRepository::map).list();
    }

    public Optional<RequestStatus> findById(long id) {
        return jdbc.sql(SELECT + " WHERE id = :id").param("id", id).query(RequestStatusRepository::map).optional();
    }

    public Optional<RequestStatus> findByCode(String codigo) {
        return jdbc.sql(SELECT + " WHERE codigo = :codigo").param("codigo", codigo).query(RequestStatusRepository::map).optional();
    }

    public void rename(long id, String nombre) {
        jdbc.sql("UPDATE estados_solicitud SET nombre = :nombre WHERE id = :id").param("nombre", nombre).param("id", id).update();
    }

    public List<Transition> activeTransitions() {
        return jdbc.sql("""
                SELECT t.id, o.codigo AS origen, d.codigo AS destino, r.codigo AS rol, t.requiere_motivo
                FROM transiciones_estado t
                JOIN estados_solicitud o ON o.id = t.estado_origen_id
                JOIN estados_solicitud d ON d.id = t.estado_destino_id
                JOIN roles r ON r.id = t.rol_id
                WHERE t.activo = 1
                """)
                .query((rs, n) -> new Transition(rs.getLong("id"), rs.getString("origen"), rs.getString("destino"),
                        rs.getString("rol"), rs.getBoolean("requiere_motivo")))
                .list();
    }

    static RequestStatus map(ResultSet rs, int n) throws SQLException {
        return new RequestStatus(rs.getLong("id"), rs.getString("codigo"), rs.getString("nombre"), rs.getBoolean("es_final"),
                rs.getInt("orden"));
    }
}
