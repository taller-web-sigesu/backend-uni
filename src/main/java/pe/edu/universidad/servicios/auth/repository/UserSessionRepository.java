package pe.edu.universidad.servicios.auth.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import pe.edu.universidad.servicios.shared.persistence.Db;
import pe.edu.universidad.servicios.shared.security.RoleCode;

@Repository
public class UserSessionRepository {

    private final JdbcClient jdbc;

    public UserSessionRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public record SessionRow(long id, long userId, String hashToken, Instant expiraEn, Instant revocadaEn) {

        public boolean isActive(Instant now) {
            return revocadaEn == null && expiraEn.isAfter(now);
        }
    }

    /** Sesión y cuenta vigentes para autenticar cada petición protegida. */
    public record SessionActorRow(long sessionId, long userId, Instant expiraEn, Instant revocadaEn, RoleCode role,
            Long areaId, String nombreCompleto, boolean activo, String estadoCuenta, boolean debeCambiarContrasena) {
    }

    public long insert(long userId, String hash, Instant now, Instant expiresAt, String ip, String userAgent) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.sql("""
                INSERT INTO sesiones_usuario (usuario_id, hash_token, emitida_en, expira_en, ip, agente_usuario, creado_en)
                VALUES (:usuario, :hash, :now, :expira, :ip, :ua, :now)
                """)
                .param("usuario", userId).param("hash", hash).param("now", Db.utc(now)).param("expira", Db.utc(expiresAt))
                .param("ip", ip).param("ua", userAgent)
                .update(keys, "id");
        return keys.getKey().longValue();
    }

    public Optional<SessionRow> findByHash(String hash) {
        return jdbc.sql("SELECT id, usuario_id, hash_token, expira_en, revocada_en FROM sesiones_usuario WHERE hash_token = :hash")
                .param("hash", hash).query(UserSessionRepository::map).optional();
    }

    public Optional<SessionRow> lockById(long id) {
        return jdbc.sql("SELECT id, usuario_id, hash_token, expira_en, revocada_en FROM sesiones_usuario WHERE id = :id FOR UPDATE")
                .param("id", id).query(UserSessionRepository::map).optional();
    }

    public void rotate(long id, String newHash) {
        jdbc.sql("UPDATE sesiones_usuario SET hash_token = :hash WHERE id = :id").param("hash", newHash).param("id", id).update();
    }

    public int revoke(long id, Instant now) {
        return jdbc.sql("UPDATE sesiones_usuario SET revocada_en = :now WHERE id = :id AND revocada_en IS NULL")
                .param("now", Db.utc(now)).param("id", id).update();
    }

    public int revokeAllForUser(long userId, Instant now) {
        return jdbc.sql("UPDATE sesiones_usuario SET revocada_en = :now WHERE usuario_id = :usuario AND revocada_en IS NULL")
                .param("now", Db.utc(now)).param("usuario", userId).update();
    }

    public Optional<SessionActorRow> findActor(long sessionId) {
        return jdbc.sql("""
                SELECT s.id AS sesion_id, s.usuario_id, s.expira_en, s.revocada_en, r.codigo AS rol, u.area_id,
                       u.nombre_completo, u.activo, u.estado_cuenta, u.debe_cambiar_contrasena
                FROM sesiones_usuario s
                JOIN usuarios u ON u.id = s.usuario_id
                JOIN roles r ON r.id = u.rol_id
                WHERE s.id = :id
                """)
                .param("id", sessionId)
                .query((rs, n) -> new SessionActorRow(
                        rs.getLong("sesion_id"), rs.getLong("usuario_id"), Db.instant(rs, "expira_en"),
                        Db.instant(rs, "revocada_en"), RoleCode.valueOf(rs.getString("rol")), Db.nullableLong(rs, "area_id"),
                        rs.getString("nombre_completo"), rs.getBoolean("activo"), rs.getString("estado_cuenta"),
                        rs.getBoolean("debe_cambiar_contrasena")))
                .optional();
    }

    public void insertConsumed(long sessionId, String hash, Instant now, Instant expiresAt) {
        jdbc.sql("""
                INSERT INTO tokens_refresh_consumidos (sesion_id, hash_token, consumido_en, expira_en)
                VALUES (:sesion, :hash, :now, :expira)
                """)
                .param("sesion", sessionId).param("hash", hash).param("now", Db.utc(now)).param("expira", Db.utc(expiresAt))
                .update();
    }

    public Optional<Long> findSessionOfConsumed(String hash) {
        return jdbc.sql("SELECT sesion_id FROM tokens_refresh_consumidos WHERE hash_token = :hash")
                .param("hash", hash).query(Long.class).optional();
    }

    /** Los hashes consumidos se conservan hasta la expiración absoluta de su sesión. */
    public int purgeExpiredConsumed(Instant now) {
        return jdbc.sql("DELETE FROM tokens_refresh_consumidos WHERE expira_en < :now").param("now", Db.utc(now)).update();
    }

    private static SessionRow map(ResultSet rs, int n) throws SQLException {
        return new SessionRow(rs.getLong("id"), rs.getLong("usuario_id"), rs.getString("hash_token"),
                Db.instant(rs, "expira_en"), Db.instant(rs, "revocada_en"));
    }
}
