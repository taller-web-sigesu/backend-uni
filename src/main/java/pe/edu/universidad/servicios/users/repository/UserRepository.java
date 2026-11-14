package pe.edu.universidad.servicios.users.repository;

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
import pe.edu.universidad.servicios.shared.security.RoleCode;

@Repository
public class UserRepository {

    private static final String SELECT = """
            SELECT u.id, r.codigo AS rol, u.area_id, a.nombre AS area_nombre, u.nombre_completo, u.correo,
                   u.hash_contrasena, u.telefono, u.activo, u.estado_cuenta, u.debe_cambiar_contrasena,
                   u.origen_registro, u.creado_en
            FROM usuarios u
            JOIN roles r ON r.id = u.rol_id
            LEFT JOIN areas a ON a.id = u.area_id
            """;

    private final JdbcClient jdbc;

    public UserRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<UserRecord> findByEmail(String correo) {
        return jdbc.sql(SELECT + " WHERE u.correo = :correo").param("correo", correo).query(UserRepository::map).optional();
    }

    public Optional<UserRecord> findById(long id) {
        return jdbc.sql(SELECT + " WHERE u.id = :id").param("id", id).query(UserRepository::map).optional();
    }

    /** Bloquea la fila de la cuenta para serializar emisión de sesiones, cambios de credencial y altas de reservas. */
    public Optional<UserRecord> lockById(long id) {
        return jdbc.sql(SELECT + " WHERE u.id = :id FOR UPDATE").param("id", id).query(UserRepository::map).optional();
    }

    public boolean emailExists(String correo, Long excludingId) {
        return jdbc.sql("SELECT COUNT(*) FROM usuarios WHERE correo = :correo AND (:id IS NULL OR id <> :id)")
                .param("correo", correo).param("id", excludingId)
                .query(Long.class).single() > 0;
    }

    public boolean adminExists() {
        return jdbc.sql("SELECT COUNT(*) FROM usuarios u JOIN roles r ON r.id = u.rol_id WHERE r.codigo = 'ADMINISTRADOR'")
                .query(Long.class).single() > 0;
    }

    public long insert(RoleCode role, Long areaId, String nombre, String correo, String hash, String telefono, Instant now) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.sql("""
                INSERT INTO usuarios (rol_id, area_id, nombre_completo, correo, hash_contrasena, telefono,
                                      activo, estado_cuenta, origen_registro, debe_cambiar_contrasena, creado_en)
                VALUES ((SELECT id FROM roles WHERE codigo = :rol), :area, :nombre, :correo, :hash, :telefono,
                        1, 'ACTIVA', 'ADMINISTRADOR', 1, :now)
                """)
                .param("rol", role.name()).param("area", areaId).param("nombre", nombre).param("correo", correo)
                .param("hash", hash).param("telefono", telefono).param("now", Db.utc(now))
                .update(keys, "id");
        return keys.getKey().longValue();
    }

    /** Alta por auto-registro (HU-27): estudiante sin área, pendiente de aprobación y sin cambio obligatorio. */
    public long insertSelfRegistered(String nombre, String correo, String hash, Instant now) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.sql("""
                INSERT INTO usuarios (rol_id, area_id, nombre_completo, correo, hash_contrasena, activo, estado_cuenta,
                                      origen_registro, debe_cambiar_contrasena, creado_en)
                VALUES ((SELECT id FROM roles WHERE codigo = 'ESTUDIANTE'), NULL, :nombre, :correo, :hash, 1, 'PENDIENTE',
                        'AUTO_REGISTRO', 0, :now)
                """)
                .param("nombre", nombre).param("correo", correo).param("hash", hash).param("now", Db.utc(now))
                .update(keys, "id");
        return keys.getKey().longValue();
    }

    /** Bandeja de HU-28: cuentas pendientes de la más antigua a la más reciente. */
    public List<UserRecord> pending(PageQuery page) {
        return jdbc.sql(SELECT + " WHERE u.estado_cuenta = 'PENDIENTE' ORDER BY u.creado_en, u.id LIMIT :limit OFFSET :offset")
                .param("limit", page.size()).param("offset", page.offset())
                .query(UserRepository::map).list();
    }

    public long countPending() {
        return jdbc.sql("SELECT COUNT(*) FROM usuarios WHERE estado_cuenta = 'PENDIENTE'").query(Long.class).single();
    }

    /** Aprueba o rechaza una cuenta; el motivo solo se conserva al rechazar. */
    public void review(long id, String estado, long reviewerId, Instant now, String motivo) {
        jdbc.sql("""
                UPDATE usuarios SET estado_cuenta = :estado, revisado_por_id = :revisor, revisado_en = :ahora,
                       motivo_rechazo = :motivo
                WHERE id = :id
                """)
                .param("estado", estado).param("revisor", reviewerId).param("ahora", Db.utc(now))
                .param("motivo", motivo).param("id", id)
                .update();
    }

    public void update(long id, RoleCode role, Long areaId, String nombre, String correo, String telefono, boolean activo) {
        jdbc.sql("""
                UPDATE usuarios
                SET rol_id = (SELECT id FROM roles WHERE codigo = :rol), area_id = :area, nombre_completo = :nombre,
                    correo = :correo, telefono = :telefono, activo = :activo
                WHERE id = :id
                """)
                .param("rol", role.name()).param("area", areaId).param("nombre", nombre).param("correo", correo)
                .param("telefono", telefono).param("activo", activo).param("id", id)
                .update();
    }

    public void updatePassword(long id, String hash, boolean mustChange) {
        jdbc.sql("UPDATE usuarios SET hash_contrasena = :hash, debe_cambiar_contrasena = :must WHERE id = :id")
                .param("hash", hash).param("must", mustChange).param("id", id).update();
    }

    public void touchLastAccess(long id, Instant now) {
        jdbc.sql("UPDATE usuarios SET ultimo_acceso_en = :now WHERE id = :id").param("now", Db.utc(now)).param("id", id).update();
    }

    public List<UserRecord> page(PageQuery page) {
        return jdbc.sql(SELECT + " ORDER BY u.id LIMIT :limit OFFSET :offset")
                .param("limit", page.size()).param("offset", page.offset())
                .query(UserRepository::map).list();
    }

    public long count() {
        return jdbc.sql("SELECT COUNT(*) FROM usuarios").query(Long.class).single();
    }

    public List<UserRecord> activeTechnicians(long areaId, PageQuery page) {
        return jdbc.sql(SELECT + """
                 WHERE r.codigo = 'TECNICO' AND u.area_id = :area AND u.activo = 1 AND u.estado_cuenta = 'ACTIVA'
                 ORDER BY u.nombre_completo, u.id LIMIT :limit OFFSET :offset
                """)
                .param("area", areaId).param("limit", page.size()).param("offset", page.offset())
                .query(UserRepository::map).list();
    }

    public long countActiveTechnicians(long areaId) {
        return jdbc.sql("""
                SELECT COUNT(*) FROM usuarios u JOIN roles r ON r.id = u.rol_id
                WHERE r.codigo = 'TECNICO' AND u.area_id = :area AND u.activo = 1 AND u.estado_cuenta = 'ACTIVA'
                """).param("area", areaId).query(Long.class).single();
    }

    public long countActiveStaffInArea(long areaId) {
        return jdbc.sql("""
                SELECT COUNT(*) FROM usuarios u JOIN roles r ON r.id = u.rol_id
                WHERE r.codigo IN ('TECNICO', 'SUPERVISOR') AND u.area_id = :area AND u.activo = 1
                """).param("area", areaId).query(Long.class).single();
    }

    static UserRecord map(ResultSet rs, int row) throws SQLException {
        return new UserRecord(
                rs.getLong("id"),
                RoleCode.valueOf(rs.getString("rol")),
                Db.nullableLong(rs, "area_id"),
                rs.getString("area_nombre"),
                rs.getString("nombre_completo"),
                rs.getString("correo"),
                rs.getString("hash_contrasena"),
                rs.getString("telefono"),
                rs.getBoolean("activo"),
                rs.getString("estado_cuenta"),
                rs.getBoolean("debe_cambiar_contrasena"),
                rs.getString("origen_registro"),
                Db.instant(rs, "creado_en"));
    }
}
