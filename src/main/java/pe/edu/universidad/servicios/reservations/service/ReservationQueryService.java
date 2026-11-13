package pe.edu.universidad.servicios.reservations.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.universidad.servicios.reservations.domain.ReservationAccessPolicy;
import pe.edu.universidad.servicios.reservations.dto.ReservationDtos.ReservationDetailResponse;
import pe.edu.universidad.servicios.reservations.dto.ReservationDtos.ReservationHistoryResponse;
import pe.edu.universidad.servicios.reservations.dto.ReservationDtos.ReservationSummaryResponse;
import pe.edu.universidad.servicios.reservations.repository.ReservationRepository;
import pe.edu.universidad.servicios.reservations.repository.ReservationRepository.ListFilter;
import pe.edu.universidad.servicios.reservations.repository.ReservationRepository.ReservationRow;
import pe.edu.universidad.servicios.shared.dto.PageQuery;
import pe.edu.universidad.servicios.shared.dto.PageResponse;
import pe.edu.universidad.servicios.shared.error.ApiException;
import pe.edu.universidad.servicios.shared.security.AuthenticatedActor;
import pe.edu.universidad.servicios.shared.security.RoleCode;
import pe.edu.universidad.servicios.shared.web.Ids;
import pe.edu.universidad.servicios.shared.web.Times;

/**
 * Listado propio, bandeja de gestión, detalle e historial de reservas (HU-23 y HU-24).
 */
@Service
@Transactional(readOnly = true)
public class ReservationQueryService {

    private static final java.util.Set<String> STATES = java.util.Set.of("PENDIENTE", "CONFIRMADA", "RECHAZADA", "CANCELADA");

    private final ReservationRepository reservations;
    private final Clock clock;

    public ReservationQueryService(ReservationRepository reservations, Clock clock) {
        this.reservations = reservations;
        this.clock = clock;
    }

    public PageResponse<ReservationSummaryResponse> list(AuthenticatedActor actor, String vista, String estado,
            String fechaDesde, String fechaHasta, PageQuery page) {
        String view = vista == null || vista.isBlank() ? "propias" : vista.trim();
        String state = estado == null || estado.isBlank() ? null : estado.trim();
        if (state != null && !STATES.contains(state)) {
            throw ApiException.invalidField("estado", "El estado no es válido.");
        }
        LocalDate desde = fechaDesde == null || fechaDesde.isBlank() ? null : SpaceQueryService.parseDate(fechaDesde);
        LocalDate hasta = fechaHasta == null || fechaHasta.isBlank() ? null : SpaceQueryService.parseDate(fechaHasta);
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw ApiException.badRequest("RANGO_INVALIDO", "fechaDesde no puede ser posterior a fechaHasta.");
        }
        ListFilter filter = switch (view) {
            case "propias" -> new ListFilter(actor.userId(), false, null, state, desde, hasta);
            case "gestion" -> {
                if (actor.role() == RoleCode.ADMINISTRADOR) {
                    yield gestion(null, state, desde, hasta);
                }
                if (actor.role() == RoleCode.SUPERVISOR && actor.areaId() != null) {
                    yield gestion(actor.areaId(), state, desde, hasta);
                }
                throw ApiException.forbidden("VISTA_NO_PERMITIDA", "La vista de gestión es para supervisores y administradores.");
            }
            default -> throw ApiException.invalidField("vista", "vista debe ser propias o gestion.");
        };
        Instant now = clock.instant();
        return new PageResponse<>(reservations.list(filter, page).stream().map(r -> ReservationMapper.summary(r, now)).toList(),
                page.page(), page.size(), reservations.count(filter));
    }

    /** La bandeja de gestión se limita a pendientes. */
    private static ListFilter gestion(Long areaId, String state, LocalDate desde, LocalDate hasta) {
        if (state != null && !state.equals("PENDIENTE")) {
            throw ApiException.invalidField("estado", "La vista de gestión solo muestra reservas PENDIENTE.");
        }
        return new ListFilter(null, true, areaId, "PENDIENTE", desde, hasta);
    }

    public ReservationDetailResponse detail(AuthenticatedActor actor, long id) {
        return ReservationMapper.detail(requireVisible(actor, id), clock.instant());
    }

    public PageResponse<ReservationHistoryResponse> history(AuthenticatedActor actor, long id, PageQuery page) {
        requireVisible(actor, id);
        return new PageResponse<>(reservations.history(id, page).stream()
                .map(h -> new ReservationHistoryResponse(h.estadoAnterior(), h.estado(), Ids.str(h.actorId()),
                        Times.iso(h.fecha()), h.motivo()))
                .toList(), page.page(), page.size(), reservations.countHistory(id));
    }

    private ReservationRow requireVisible(AuthenticatedActor actor, long id) {
        return reservations.findById(id).filter(r -> ReservationAccessPolicy.canSee(actor, r))
                .orElseThrow(ReservationQueryService::notFound);
    }

    static ApiException notFound() {
        return ApiException.notFound("La reserva no existe o no está en su alcance.");
    }
}
