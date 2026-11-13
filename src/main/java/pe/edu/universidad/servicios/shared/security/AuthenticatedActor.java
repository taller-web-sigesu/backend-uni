package pe.edu.universidad.servicios.shared.security;

import java.util.Arrays;
import java.util.Objects;

import pe.edu.universidad.servicios.shared.error.ApiException;

/**
 * Actor autenticado con rol y área vigentes, leídos de la base en cada petición.
 */
public record AuthenticatedActor(
        long userId,
        long sessionId,
        RoleCode role,
        Long areaId,
        String nombreCompleto,
        boolean mustChangePassword) {

    public boolean is(RoleCode... roles) {
        return Arrays.asList(roles).contains(role);
    }

    public void requireRole(RoleCode... roles) {
        if (!is(roles)) {
            throw ApiException.forbidden();
        }
    }

    public boolean isSupervisorOf(Long area) {
        return role == RoleCode.SUPERVISOR && areaId != null && Objects.equals(areaId, area);
    }
}
