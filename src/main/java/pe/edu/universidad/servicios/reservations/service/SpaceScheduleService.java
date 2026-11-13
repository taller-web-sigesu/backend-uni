package pe.edu.universidad.servicios.reservations.service;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.universidad.servicios.audit.service.AuditEventService;
import pe.edu.universidad.servicios.reservations.dto.ReservationDtos.ReplaceSpaceScheduleRequest;
import pe.edu.universidad.servicios.reservations.dto.ReservationDtos.ReservationPolicyRequest;
import pe.edu.universidad.servicios.reservations.dto.ReservationDtos.ReservationPolicyResponse;
import pe.edu.universidad.servicios.reservations.dto.ReservationDtos.ScheduleSlotRequest;
import pe.edu.universidad.servicios.reservations.dto.ReservationDtos.ScheduleSlotResponse;
import pe.edu.universidad.servicios.reservations.dto.ReservationDtos.SpaceScheduleResponse;
import pe.edu.universidad.servicios.reservations.repository.SpaceConfigRepository;
import pe.edu.universidad.servicios.reservations.repository.SpaceConfigRepository.ReservationPolicy;
import pe.edu.universidad.servicios.reservations.repository.SpaceConfigRepository.ScheduleSlot;
import pe.edu.universidad.servicios.reservations.repository.SpaceRepository;
import pe.edu.universidad.servicios.shared.error.ApiException;
import pe.edu.universidad.servicios.shared.security.AuthenticatedActor;
import pe.edu.universidad.servicios.shared.security.RoleCode;
import pe.edu.universidad.servicios.shared.web.Times;

/**
 * Horario semanal por espacio (una franja por día) y política global de reservas (HU-25).
 */
@Service
public class SpaceScheduleService {

    private final SpaceRepository spaces;
    private final SpaceConfigRepository config;
    private final AuditEventService audit;
    private final Clock clock;

    public SpaceScheduleService(SpaceRepository spaces, SpaceConfigRepository config, AuditEventService audit, Clock clock) {
        this.spaces = spaces;
        this.config = config;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public SpaceScheduleResponse schedule(AuthenticatedActor actor, long spaceId) {
        actor.requireRole(RoleCode.ADMINISTRADOR);
        spaces.findById(spaceId).orElseThrow(() -> ApiException.notFound("El espacio no existe."));
        return toResponse(config.activeSchedule(spaceId));
    }

    @Transactional
    public SpaceScheduleResponse replaceSchedule(AuthenticatedActor actor, long spaceId, ReplaceSpaceScheduleRequest body) {
        actor.requireRole(RoleCode.ADMINISTRADOR);
        Set<Integer> days = new HashSet<>();
        for (ScheduleSlotRequest slot : body.franjas()) {
            if (slot == null) {
                throw ApiException.invalidField("franjas", "Cada franja es obligatoria.");
            }
            if (!days.add(slot.diaSemana())) {
                throw ApiException.invalidField("franjas", "Solo se admite una franja por día (día " + slot.diaSemana() + " repetido).");
            }
            if (!slot.horaApertura().isBefore(slot.horaCierre())) {
                throw ApiException.invalidField("franjas", "horaApertura debe ser anterior a horaCierre.");
            }
        }
        spaces.lockById(spaceId).orElseThrow(() -> ApiException.notFound("El espacio no existe."));
        for (int day = 1; day <= 7; day++) {
            final int d = day;
            body.franjas().stream().filter(s -> s.diaSemana() == d).findFirst().ifPresentOrElse(
                    s -> config.upsertSlot(spaceId, new ScheduleSlot(d, s.horaApertura(), s.horaCierre())),
                    () -> config.closeDay(spaceId, d));
        }
        audit.record("horarios_espacio", spaceId, null, "REEMPLAZAR", "franjas", null, String.valueOf(days.size()), actor.userId());
        return toResponse(config.activeSchedule(spaceId));
    }

    @Transactional(readOnly = true)
    public ReservationPolicyResponse policy(AuthenticatedActor actor) {
        actor.requireRole(RoleCode.ADMINISTRADOR);
        return config.currentPolicy(clock.instant()).map(SpaceScheduleService::toResponse)
                .orElseThrow(() -> ApiException.notFound("No hay una política de reservas vigente."));
    }

    /** Sustituye la política vigente; se aplica a nuevas reservas. */
    @Transactional
    public ReservationPolicyResponse replacePolicy(AuthenticatedActor actor, ReservationPolicyRequest body) {
        actor.requireRole(RoleCode.ADMINISTRADOR);
        if (body.duracionMaximaMinutos() < body.duracionMinimaMinutos()) {
            throw ApiException.badRequest("POLITICA_INVALIDA", "duracionMaximaMinutos debe ser mayor o igual que duracionMinimaMinutos.");
        }
        Instant now = clock.instant();
        long id = config.replacePolicy(body.duracionMinimaMinutos(), body.duracionMaximaMinutos(), body.horasAnticipacionMinima(),
                body.diasAnticipacionMaxima(), body.maxReservasActivasPorDia(), now);
        audit.record("politicas_reserva", id, null, "REEMPLAZAR", "maxReservasActivasPorDia", null,
                String.valueOf(body.maxReservasActivasPorDia()), actor.userId());
        return config.currentPolicy(now).map(SpaceScheduleService::toResponse).orElseThrow();
    }

    private static SpaceScheduleResponse toResponse(List<ScheduleSlot> slots) {
        return new SpaceScheduleResponse(slots.stream()
                .map(s -> new ScheduleSlotResponse(s.diaSemana(), Times.hhmm(s.horaApertura()), Times.hhmm(s.horaCierre())))
                .toList());
    }

    private static ReservationPolicyResponse toResponse(ReservationPolicy p) {
        return new ReservationPolicyResponse(p.duracionMinimaMinutos(), p.duracionMaximaMinutos(), p.horasAnticipacionMinima(),
                p.diasAnticipacionMaxima(), p.maxReservasActivasPorDia());
    }
}
