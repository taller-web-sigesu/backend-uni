package pe.edu.universidad.servicios.reservations.domain;

import pe.edu.universidad.servicios.reservations.repository.ReservationRepository.ReservationRow;
import pe.edu.universidad.servicios.shared.security.AuthenticatedActor;
import pe.edu.universidad.servicios.shared.security.RoleCode;

/**
 * Alcance sobre reservas: propietario, supervisor del área del espacio o administrador.
 * Un espacio sin área solo lo gestiona el administrador.
 */
public final class ReservationAccessPolicy {

    private ReservationAccessPolicy() {
    }

    public static boolean canSee(AuthenticatedActor actor, ReservationRow r) {
        return r.solicitanteId() == actor.userId() || canManage(actor, r);
    }

    public static boolean canManage(AuthenticatedActor actor, ReservationRow r) {
        return actor.role() == RoleCode.ADMINISTRADOR || (r.espacioAreaId() != null && actor.isSupervisorOf(r.espacioAreaId()));
    }
}
