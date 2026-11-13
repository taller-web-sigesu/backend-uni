package pe.edu.universidad.servicios.users.service;

import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import pe.edu.universidad.servicios.shared.security.AuthenticatedActor;
import pe.edu.universidad.servicios.shared.security.RoleCode;
import pe.edu.universidad.servicios.users.dto.UserDtos.RoleResponse;

@Service
public class RoleQueryService {

    private final JdbcClient jdbc;

    public RoleQueryService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<RoleResponse> list(AuthenticatedActor actor) {
        actor.requireRole(RoleCode.ADMINISTRADOR);
        return jdbc.sql("""
                SELECT codigo, nombre FROM roles WHERE activo = 1
                ORDER BY FIELD(codigo, 'ESTUDIANTE', 'DOCENTE', 'TECNICO', 'SUPERVISOR', 'ADMINISTRADOR'), id
                """)
                .query((rs, n) -> new RoleResponse(rs.getString("codigo"), rs.getString("nombre")))
                .list();
    }
}
