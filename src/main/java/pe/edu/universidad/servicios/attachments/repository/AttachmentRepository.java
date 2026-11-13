package pe.edu.universidad.servicios.attachments.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import pe.edu.universidad.servicios.shared.persistence.Db;

@Repository
public class AttachmentRepository {

    private static final String SELECT = """
            SELECT a.id, a.solicitud_id, a.autor_id, u.nombre_completo AS autor_nombre, a.nombre_original,
                   a.proveedor_almacenamiento, a.clave_objeto, a.tipo_mime, a.tamano_bytes, a.creado_en
            FROM adjuntos_solicitud a
            JOIN usuarios u ON u.id = a.autor_id
            """;

    private final JdbcClient jdbc;

    public AttachmentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public long countActive(long solicitudId) {
        return jdbc.sql("SELECT COUNT(*) FROM adjuntos_solicitud WHERE solicitud_id = :s AND eliminado_en IS NULL")
                .param("s", solicitudId).query(Long.class).single();
    }

    public List<AttachmentRow> listActive(long solicitudId) {
        return jdbc.sql(SELECT + " WHERE a.solicitud_id = :s AND a.eliminado_en IS NULL ORDER BY a.creado_en, a.id")
                .param("s", solicitudId).query(AttachmentRepository::map).list();
    }

    /** Evidencia vigente de esa solicitud; un id de otra solicitud no se encuentra. */
    public Optional<AttachmentRow> findActive(long solicitudId, long id) {
        return jdbc.sql(SELECT + " WHERE a.id = :id AND a.solicitud_id = :s AND a.eliminado_en IS NULL")
                .param("id", id).param("s", solicitudId).query(AttachmentRepository::map).optional();
    }

    public long insert(long solicitudId, long autorId, String nombre, String proveedor, String clave, String mime,
            long tamano, String hashSha256, Instant now) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.sql("""
                INSERT INTO adjuntos_solicitud (solicitud_id, autor_id, nombre_original, proveedor_almacenamiento,
                                                clave_objeto, tipo_mime, tamano_bytes, hash_sha256, creado_en)
                VALUES (:s, :autor, :nombre, :proveedor, :clave, :mime, :tamano, :hash, :now)
                """)
                .param("s", solicitudId).param("autor", autorId).param("nombre", nombre).param("proveedor", proveedor)
                .param("clave", clave).param("mime", mime).param("tamano", tamano).param("hash", hashSha256)
                .param("now", Db.utc(now))
                .update(keys, "id");
        return keys.getKey().longValue();
    }

    public void markDeleted(long id, long actorId, Instant now) {
        jdbc.sql("UPDATE adjuntos_solicitud SET eliminado_en = :now, eliminado_por_id = :actor WHERE id = :id")
                .param("now", Db.utc(now)).param("actor", actorId).param("id", id).update();
    }

    static AttachmentRow map(ResultSet rs, int row) throws SQLException {
        return new AttachmentRow(
                rs.getLong("id"),
                rs.getLong("solicitud_id"),
                rs.getLong("autor_id"),
                rs.getString("autor_nombre"),
                rs.getString("nombre_original"),
                rs.getString("proveedor_almacenamiento"),
                rs.getString("clave_objeto"),
                rs.getString("tipo_mime"),
                rs.getLong("tamano_bytes"),
                Db.instant(rs, "creado_en"));
    }
}
