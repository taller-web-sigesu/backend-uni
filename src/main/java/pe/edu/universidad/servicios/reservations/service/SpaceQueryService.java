package pe.edu.universidad.servicios.reservations.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.universidad.servicios.reservations.domain.ReservationTimePolicy;
import pe.edu.universidad.servicios.reservations.dto.ReservationDtos.DayScheduleResponse;
import pe.edu.universidad.servicios.reservations.dto.ReservationDtos.IdName;
import pe.edu.universidad.servicios.reservations.dto.ReservationDtos.SpaceAvailabilityResponse;
import pe.edu.universidad.servicios.reservations.dto.ReservationDtos.SpaceSummaryResponse;
import pe.edu.universidad.servicios.reservations.dto.ReservationDtos.TimeSlotResponse;
import pe.edu.universidad.servicios.reservations.repository.ReservationRepository;
import pe.edu.universidad.servicios.reservations.repository.ReservationRepository.TimeRange;
import pe.edu.universidad.servicios.reservations.repository.SpaceConfigRepository;
import pe.edu.universidad.servicios.reservations.repository.SpaceConfigRepository.ScheduleSlot;
import pe.edu.universidad.servicios.reservations.repository.SpaceRepository;
import pe.edu.universidad.servicios.reservations.repository.SpaceRepository.Space;
import pe.edu.universidad.servicios.reservations.repository.SpaceRepository.SpaceFilter;
import pe.edu.universidad.servicios.shared.dto.PageQuery;
import pe.edu.universidad.servicios.shared.dto.PageResponse;
import pe.edu.universidad.servicios.shared.error.ApiException;
import pe.edu.universidad.servicios.shared.web.Ids;
import pe.edu.universidad.servicios.shared.web.Times;

/**
 * Consulta de espacios activos y disponibilidad informativa (HU-21).
 */
@Service
@Transactional(readOnly = true)
public class SpaceQueryService {

    private final SpaceRepository spaces;
    private final SpaceConfigRepository config;
    private final ReservationRepository reservations;
    private final Clock clock;

    public SpaceQueryService(SpaceRepository spaces, SpaceConfigRepository config, ReservationRepository reservations,
            Clock clock) {
        this.spaces = spaces;
        this.config = config;
        this.reservations = reservations;
        this.clock = clock;
    }

    public PageResponse<SpaceSummaryResponse> list(String tipoEspacioId, String pabellon, Integer capacidadMinima,
            String fecha, PageQuery page) {
        if (capacidadMinima != null && capacidadMinima < 1) {
            throw ApiException.invalidField("capacidadMinima", "capacidadMinima debe ser positiva.");
        }
        LocalDate day = fecha == null || fecha.isBlank() ? ReservationTimePolicy.today(clock.instant()) : parseDate(fecha);
        SpaceFilter filter = new SpaceFilter(Ids.parse("tipoEspacioId", tipoEspacioId),
                pabellon == null || pabellon.isBlank() ? null : pabellon.trim(), capacidadMinima);
        List<SpaceSummaryResponse> items = spaces.searchBookable(filter, page).stream()
                .map(s -> summary(s, config.activeSlot(s.id(), day.getDayOfWeek().getValue())))
                .toList();
        return new PageResponse<>(items, page.page(), page.size(), spaces.countBookable(filter));
    }

    public SpaceAvailabilityResponse availability(long spaceId, String fecha) {
        if (fecha == null || fecha.isBlank()) {
            throw ApiException.invalidField("fecha", "fecha es obligatoria (YYYY-MM-DD).");
        }
        LocalDate day = parseDate(fecha);
        Space space = spaces.findById(spaceId).filter(Space::bookable)
                .orElseThrow(() -> ApiException.notFound("El espacio no existe o no está activo."));
        Optional<ScheduleSlot> schedule = config.activeSlot(space.id(), day.getDayOfWeek().getValue());
        if (schedule.isEmpty()) {
            return new SpaceAvailabilityResponse(Ids.str(space.id()), day.toString(), null, List.of(), List.of());
        }
        List<TimeRange> occupied = reservations.occupied(space.id(), day);
        return new SpaceAvailabilityResponse(Ids.str(space.id()), day.toString(), schedule(schedule.get()),
                slots(ReservationTimePolicy.freeRanges(schedule.get(), occupied)), slots(occupied));
    }

    static LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException ex) {
            throw ApiException.invalidField("fecha", "fecha debe tener el formato YYYY-MM-DD.");
        }
    }

    private static List<TimeSlotResponse> slots(List<TimeRange> ranges) {
        return ranges.stream().map(r -> new TimeSlotResponse(Times.hhmm(r.inicio()), Times.hhmm(r.fin()))).toList();
    }

    private static DayScheduleResponse schedule(ScheduleSlot slot) {
        return new DayScheduleResponse(Times.hhmm(slot.horaApertura()), Times.hhmm(slot.horaCierre()));
    }

    private static SpaceSummaryResponse summary(Space s, Optional<ScheduleSlot> slot) {
        return new SpaceSummaryResponse(Ids.str(s.id()), s.codigo(), s.nombre(), new IdName(Ids.str(s.tipoId()), s.tipoNombre()),
                Ids.str(s.areaId()), s.pabellon(), s.piso(), s.capacidad(), slot.map(SpaceQueryService::schedule).orElse(null));
    }
}
