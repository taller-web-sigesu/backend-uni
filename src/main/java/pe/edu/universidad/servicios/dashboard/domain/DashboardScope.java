package pe.edu.universidad.servicios.dashboard.domain;

import java.time.Instant;
import java.util.Objects;

import pe.edu.universidad.servicios.shared.error.ApiException;
import pe.edu.universidad.servicios.shared.security.AuthenticatedActor;
import pe.edu.universidad.servicios.shared.security.RoleCode;

/**
 * Alcance efectivo del dashboard: intervalo [desde, hasta) sobre la fecha de registro y área.
 * El supervisor usa su área y no puede pedir otra; el administrador elige un área o todas.
 */
public record DashboardScope(Instant desde, Instant hasta, Long areaId) {

    public static DashboardScope resolve(AuthenticatedActor actor, Instant desde, Instant hasta, Long requestedArea) {
        actor.requireRole(RoleCode.SUPERVISOR, RoleCode.ADMINISTRADOR);
        if (desde == null || hasta == null) {
            throw ApiException.badRequest("RANGO_INVALIDO", "fechaDesde y fechaHasta son obligatorios.");
        }
        if (!desde.isBefore(hasta)) {
            throw ApiException.badRequest("RANGO_INVALIDO", "fechaDesde debe ser anterior a fechaHasta.");
        }
        if (actor.role() == RoleCode.SUPERVISOR) {
            if (actor.areaId() == null) {
                throw ApiException.forbidden("SIN_AREA", "El supervisor no tiene un área asignada.");
            }
            if (requestedArea != null && !Objects.equals(requestedArea, actor.areaId())) {
                throw ApiException.forbidden("AREA_FUERA_DE_ALCANCE", "El supervisor solo puede consultar su área.");
            }
            return new DashboardScope(desde, hasta, actor.areaId());
        }
        return new DashboardScope(desde, hasta, requestedArea);
    }
}
