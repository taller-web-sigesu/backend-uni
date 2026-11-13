package pe.edu.universidad.servicios.requests.service;

import java.util.Objects;

import org.springframework.stereotype.Service;

import pe.edu.universidad.servicios.requests.repository.RequestRow;
import pe.edu.universidad.servicios.requests.repository.ServiceRequestRepository;
import pe.edu.universidad.servicios.shared.error.ApiException;
import pe.edu.universidad.servicios.shared.security.AuthenticatedActor;
import pe.edu.universidad.servicios.shared.security.RoleCode;

/**
 * Reglas de alcance compartidas por detalle, historial y comentarios: propietario, técnico con
 * asignación vigente o supervisor del área. Fuera de alcance responde 404.
 */
@Service
public class RequestAccessService {

    private final ServiceRequestRepository requests;

    public RequestAccessService(ServiceRequestRepository requests) {
        this.requests = requests;
    }

    public RequestRow requireVisible(AuthenticatedActor actor, long requestId) {
        requireParticipantRole(actor);
        RequestRow row = requests.findById(requestId).orElseThrow(RequestAccessService::notFound);
        if (!canSee(actor, row)) {
            throw notFound();
        }
        return row;
    }

    /** Igual que {@link #requireVisible}, pero bloquea la fila de la solicitud ({@code FOR UPDATE}) en la transacción actual. */
    public RequestRow lockVisible(AuthenticatedActor actor, long requestId) {
        requireParticipantRole(actor);
        RequestRow row = requests.lockById(requestId).orElseThrow(RequestAccessService::notFound);
        if (!canSee(actor, row)) {
            throw notFound();
        }
        return row;
    }

    public static boolean canSee(AuthenticatedActor actor, RequestRow row) {
        return switch (actor.role()) {
            case ESTUDIANTE, DOCENTE -> row.solicitanteId() == actor.userId();
            case TECNICO -> Objects.equals(row.responsableId(), actor.userId());
            case SUPERVISOR -> actor.isSupervisorOf(row.areaId());
            case ADMINISTRADOR -> false;
        };
    }

    /** El administrador no participa del flujo de solicitudes. */
    public static void requireParticipantRole(AuthenticatedActor actor) {
        actor.requireRole(RoleCode.ESTUDIANTE, RoleCode.DOCENTE, RoleCode.TECNICO, RoleCode.SUPERVISOR);
    }

    public static ApiException notFound() {
        return ApiException.notFound("La solicitud no existe o no está en su alcance.");
    }
}
