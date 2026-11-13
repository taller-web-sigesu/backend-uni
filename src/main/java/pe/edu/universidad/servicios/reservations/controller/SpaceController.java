package pe.edu.universidad.servicios.reservations.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import pe.edu.universidad.servicios.reservations.dto.ReservationDtos.SpaceAvailabilityResponse;
import pe.edu.universidad.servicios.reservations.dto.ReservationDtos.SpaceSummaryResponse;
import pe.edu.universidad.servicios.reservations.service.SpaceQueryService;
import pe.edu.universidad.servicios.shared.dto.PageQuery;
import pe.edu.universidad.servicios.shared.dto.PageResponse;
import pe.edu.universidad.servicios.shared.security.AuthenticatedActor;
import pe.edu.universidad.servicios.shared.security.CurrentActor;

@RestController
@RequestMapping("/api/v1/spaces")
@Tag(name = "Reserva de ambientes", description = "HU-21 a HU-25")
public class SpaceController {

    private final SpaceQueryService spaces;

    public SpaceController(SpaceQueryService spaces) {
        this.spaces = spaces;
    }

    @GetMapping
    @Operation(summary = "Lista espacios activos y su horario para el día elegido")
    public PageResponse<SpaceSummaryResponse> list(@CurrentActor AuthenticatedActor actor,
            @RequestParam(required = false) String tipoEspacioId, @RequestParam(required = false) String pabellon,
            @RequestParam(required = false) Integer capacidadMinima, @RequestParam(required = false) String fecha,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return spaces.list(tipoEspacioId, pabellon, capacidadMinima, fecha, PageQuery.of(page, size));
    }

    @GetMapping("/{id}/availability")
    @Operation(summary = "Consulta franjas libres y ocupadas de un ambiente activo")
    public SpaceAvailabilityResponse availability(@CurrentActor AuthenticatedActor actor, @PathVariable long id,
            @RequestParam(required = false) String fecha) {
        return spaces.availability(id, fecha);
    }
}
