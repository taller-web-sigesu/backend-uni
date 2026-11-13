package pe.edu.universidad.servicios.support;

import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Cuentas ficticias creadas directamente en base para preparar escenarios.
 */
public class TestData {

    public static final String PASSWORD = "Prueba2026x";

    private final JdbcClient jdbc;
    private final PasswordEncoder encoder;
    private String cachedHash;

    public TestData(JdbcClient jdbc, PasswordEncoder encoder) {
        this.jdbc = jdbc;
        this.encoder = encoder;
    }

    public record Account(long id, String correo, String password, String role, Long areaId) {
    }

    public Account user(String role, Long areaId) {
        return user(role, areaId, false);
    }

    public Account user(String role, Long areaId, boolean mustChange) {
        if (cachedHash == null) {
            cachedHash = encoder.encode(PASSWORD);
        }
        String correo = role.toLowerCase() + "-" + UUID.randomUUID().toString().substring(0, 8) + "@universidad.example";
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.sql("""
                INSERT INTO usuarios (rol_id, area_id, nombre_completo, correo, hash_contrasena, debe_cambiar_contrasena)
                VALUES ((SELECT id FROM roles WHERE codigo = :rol), :area, :nombre, :correo, :hash, :must)
                """)
                .param("rol", role).param("area", areaId).param("nombre", "Demo " + role)
                .param("correo", correo).param("hash", cachedHash).param("must", mustChange)
                .update(keys, "id");
        return new Account(keys.getKey().longValue(), correo, PASSWORD, role, areaId);
    }

    public long area() {
        String code = "AREA_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.sql("INSERT INTO areas (codigo, nombre) VALUES (:c, :n)").param("c", code).param("n", "Área " + code)
                .update(keys, "id");
        return keys.getKey().longValue();
    }

    /** Categoría activa en el área con la prioridad predeterminada indicada (2 = MEDIA). */
    public long category(long areaId, long prioridadId) {
        String code = "CAT_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.sql("INSERT INTO categorias (area_id, prioridad_predeterminada_id, codigo, nombre) VALUES (:a, :p, :c, :n)")
                .param("a", areaId).param("p", prioridadId).param("c", code).param("n", "Categoría " + code)
                .update(keys, "id");
        return keys.getKey().longValue();
    }

    public JdbcClient jdbc() {
        return jdbc;
    }
}
