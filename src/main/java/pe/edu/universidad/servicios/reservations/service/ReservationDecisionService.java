package pe.edu.universidad.servicios.reservations.service;

import java.time.Clock;
import java.time.Instant;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.universidad.servicios.audit.service.AuditEventService;
import pe.edu.universidad.servicios.reservations.domain.ReservationAccessPolicy;
import pe.edu.universidad.servicios.reservations.domain.ReservationTimePolicy;
import pe.edu.universidad.servicios.reservations.dto.ReservationDtos.ChangeReservationStatusRequest;
import pe.edu.universidad.servicios.reservations.dto.ReservationDtos.ReservationDetailResponse;
import pe.edu.universidad.servicios.reservations.repository.ReservationRepository;
import pe.edu.universidad.servicios.reservations.repository.ReservationRepository.ReservationRow;
import pe.edu.universidad.servicios.reservations.repository.SpaceRepository;
import pe.edu.universidad.servicios.shared.error.ApiException;
import pe.edu.universidad.servicios.shared.security.AuthenticatedActor;

/**
 * Confirmación, rechazo (HU-23) y cancelación (HU-24). Solo el propietario cancela; el supervisor del
 * área o el administrador decide sobre pendientes, sin autoaprobación.
 */
@Service
public class ReservationDecisionService {

    private final ReservationRepository reservations;
    private final SpaceRepository spaces;
    private final AuditEventService audit;
    private final Clock clock;

    public ReservationDecisionService(ReservationRepository reservations, SpaceRepository spaces, AuditEventService audit,
            Clock clock) {
        this.reservations = reservations;
        this.spaces = spaces;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReservationDetailResponse changeStatus(AuthenticatedActor actor, long id, ChangeReservationStatusRequest body) {
        String target = body.estado().trim();
        if (!target.equals("CONFIRMADA") && !target.equals("RECHAZADA") && !target.equals("CANCELADA")) {
            throw ApiException.invalidField("estado", "estado debe ser CONFIRMADA, RECHAZADA o CANCELADA.");
        }
        ReservationRow snapshot = reservations.findById(id)
                .filter(r -> ReservationAccessPolicy.canSee(actor, r))
                .orElseThrow(ReservationQueryService::notFound);
        return target.equals("CANCELADA") ? cancel(actor, snapshot) : decide(actor, snapshot, target, body.motivo());
    }

    private ReservationDetailResponse decide(AuthenticatedActor actor, ReservationRow snapshot, String target, String motivo) {
        if (!ReservationAccessPolicy.canManage(actor, snapshot)) {
            throw ApiException.forbidden("DECISION_NO_PERMITIDA", "Solo el supervisor del área o el administrador decide la reserva.");
        }
        if (snapshot.solicitanteId() == actor.userId()) {
            throw ApiException.forbidden("AUTOAPROBACION", "No puede decidir sobre su propia reserva.");
        }
        boolean reject = target.equals("RECHAZADA");
        if (reject && (motivo == null || motivo.isBlank())) {
            throw ApiException.invalidField("motivo", "El motivo es obligatorio al rechazar.");
        }
        // Orden de bloqueo: espacio y luego reserva, igual que en el alta.
        spaces.lockById(snapshot.espacioId());
        ReservationRow current = reservations.lockById(snapshot.id()).orElseThrow(ReservationQueryService::notFound);
        if (!"PENDIENTE".equals(current.estado())) {
            throw ApiException.conflict("TRANSICION_INVALIDA", "Solo se decide sobre reservas pendientes.");
        }
        Instant now = clock.instant();
        long targetId = reservations.stateId(target);
        boolean updated;
        String reason = null;
        if (reject) {
            reason = motivo.trim();
            updated = reservations.reject(current.id(), current.version(), targetId, reason, now);
        } else {
            if (!now.isBefore(ReservationTimePolicy.toInstant(current.fecha(), current.horaInicio()))) {
                throw ApiException.conflict("INICIO_ALCANZADO", "La reserva ya comenzó y no puede confirmarse.");
            }
            if (reservations.overlaps(current.espacioId(), current.fecha(), current.horaInicio(), current.horaFin(), current.id())) {
                throw ReservationService.slotTaken();
            }
            updated = reservations.confirm(current.id(), current.version(), targetId, actor.userId(), now);
        }
        return finish(current, updated, target, reason, actor, now);
    }

    private ReservationDetailResponse cancel(AuthenticatedActor actor, ReservationRow snapshot) {
        if (snapshot.solicitanteId() != actor.userId()) {
            throw ApiException.forbidden("CANCELACION_NO_PERMITIDA", "Solo el propietario puede cancelar la reserva.");
        }
        ReservationRow current = reservations.lockById(snapshot.id()).orElseThrow(ReservationQueryService::notFound);
        if (!"PENDIENTE".equals(current.estado()) && !"CONFIRMADA".equals(current.estado())) {
            throw ApiException.conflict("TRANSICION_INVALIDA", "Solo se cancelan reservas pendientes o confirmadas.");
        }
        Instant now = clock.instant();
        if (!now.isBefore(ReservationTimePolicy.toInstant(current.fecha(), current.horaInicio()))) {
            throw ApiException.conflict("INICIO_ALCANZADO", "La reserva ya comenzó y no puede cancelarse.");
        }
        boolean updated;
        try {
            updated = reservations.cancel(current.id(), current.version(), reservations.stateId("CANCELADA"), actor.userId(), now);
        } catch (DuplicateKeyException ex) {
            throw ApiException.conflict("CONFLICTO_CONCURRENCIA", "La reserva fue modificada por otra operación.");
        }
        return finish(current, updated, "CANCELADA", null, actor, now);
    }

    private ReservationDetailResponse finish(ReservationRow current, boolean updated, String target, String reason,
            AuthenticatedActor actor, Instant now) {
        if (!updated) {
            throw ApiException.conflict("CONFLICTO_CONCURRENCIA", "La reserva fue modificada por otra operación. Vuelva a consultarla.");
        }
        reservations.insertHistory(current.id(), reservations.stateId(current.estado()), reservations.stateId(target),
                actor.userId(), reason, now);
        audit.record("reservas", current.id(), null, "CAMBIAR_ESTADO", "estado", current.estado(), target, actor.userId());
        return ReservationMapper.detail(reservations.findById(current.id()).orElseThrow(), now);
    }
}
