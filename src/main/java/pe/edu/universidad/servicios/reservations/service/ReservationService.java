package pe.edu.universidad.servicios.reservations.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.universidad.servicios.audit.service.AuditEventService;
import pe.edu.universidad.servicios.reservations.domain.ReservationTimePolicy;
import pe.edu.universidad.servicios.reservations.dto.ReservationDtos.CreateReservationRequest;
import pe.edu.universidad.servicios.reservations.dto.ReservationDtos.ReservationSummaryResponse;
import pe.edu.universidad.servicios.reservations.repository.ReservationRepository;
import pe.edu.universidad.servicios.reservations.repository.SpaceConfigRepository;
import pe.edu.universidad.servicios.reservations.repository.SpaceConfigRepository.ReservationPolicy;
import pe.edu.universidad.servicios.reservations.repository.SpaceRepository;
import pe.edu.universidad.servicios.reservations.repository.SpaceRepository.Space;
import pe.edu.universidad.servicios.shared.error.ApiException;
import pe.edu.universidad.servicios.shared.security.AuthenticatedActor;
import pe.edu.universidad.servicios.shared.web.Ids;
import pe.edu.universidad.servicios.users.service.UserIdentityService;

/**
 * Alta de reservas (HU-22). Bloquea primero la cuenta del solicitante (límite diario entre espacios)
 * y luego el espacio (solapamientos); con READ COMMITTED las comprobaciones ven lo ya confirmado.
 */
@Service
public class ReservationService {

    private final ReservationRepository reservations;
    private final SpaceRepository spaces;
    private final SpaceConfigRepository config;
    private final UserIdentityService identity;
    private final AuditEventService audit;
    private final Clock clock;

    public ReservationService(ReservationRepository reservations, SpaceRepository spaces, SpaceConfigRepository config,
            UserIdentityService identity, AuditEventService audit, Clock clock) {
        this.reservations = reservations;
        this.spaces = spaces;
        this.config = config;
        this.identity = identity;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReservationSummaryResponse create(AuthenticatedActor actor, CreateReservationRequest body) {
        long spaceId = Ids.required("espacioId", body.espacioId());
        Instant now = clock.instant();
        identity.lock(actor.userId()).orElseThrow(() -> ApiException.unauthorized("SESION_INVALIDA", "La cuenta no está disponible."));
        Space space = spaces.lockById(spaceId).filter(Space::bookable)
                .orElseThrow(() -> ApiException.notFound("El espacio no existe o no está disponible para reservas."));
        ReservationPolicy policy = config.currentPolicy(now)
                .orElseThrow(() -> ApiException.conflict("POLITICA_NO_CONFIGURADA", "No hay una política de reservas vigente."));
        ReservationTimePolicy.validateNewSlot(body.fecha(), body.horaInicio(), body.horaFin(), now, policy,
                config.activeSlot(space.id(), body.fecha().getDayOfWeek().getValue()).orElse(null));
        if (body.numAsistentes() != null && body.numAsistentes() > space.capacidad()) {
            throw ApiException.badRequest("CAPACIDAD_EXCEDIDA", "numAsistentes supera la capacidad del espacio (" + space.capacidad() + ").");
        }
        if (reservations.countActiveForRequester(actor.userId(), body.fecha()) >= policy.maxReservasActivasPorDia()) {
            throw ApiException.conflict("LIMITE_DIARIO", "Se alcanzó el máximo de " + policy.maxReservasActivasPorDia()
                    + " reservas pendientes o confirmadas para esa fecha.");
        }
        if (reservations.overlaps(space.id(), body.fecha(), body.horaInicio(), body.horaFin(), null)) {
            throw slotTaken();
        }
        long pendiente = reservations.stateId("PENDIENTE");
        String codigo = reservations.nextCode(now.atZone(ZoneOffset.UTC).getYear());
        long id;
        try {
            id = reservations.insert(codigo, space.id(), actor.userId(), pendiente, body.fecha(), body.horaInicio(),
                    body.horaFin(), body.motivo().trim(), body.numAsistentes(), now);
        } catch (DuplicateKeyException ex) {
            throw slotTaken();
        }
        reservations.insertHistory(id, null, pendiente, actor.userId(), null, now);
        audit.record("reservas", id, null, "CREAR", "estado", null, "PENDIENTE", actor.userId());
        return ReservationMapper.summary(reservations.findById(id).orElseThrow(), now);
    }

    static ApiException slotTaken() {
        return ApiException.conflict("FRANJA_OCUPADA", "La franja seleccionada ya está ocupada.");
    }
}
