package pe.edu.universidad.servicios.reservations.controller;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import pe.edu.universidad.servicios.reservations.dto.ReservationDtos.CreateSpaceRequest;
import pe.edu.universidad.servicios.reservations.dto.ReservationDtos.CreateSpaceTypeRequest;
import pe.edu.universidad.servicios.reservations.dto.ReservationDtos.ReplaceSpaceScheduleRequest;
import pe.edu.universidad.servicios.reservations.dto.ReservationDtos.ReservationPolicyRequest;
import pe.edu.universidad.servicios.reservations.dto.ReservationDtos.ReservationPolicyResponse;
import pe.edu.universidad.servicios.reservations.dto.ReservationDtos.SpaceResponse;
import pe.edu.universidad.servicios.reservations.dto.ReservationDtos.SpaceScheduleResponse;
import pe.edu.universidad.servicios.reservations.dto.ReservationDtos.SpaceTypeResponse;
import pe.edu.universidad.servicios.reservations.dto.ReservationDtos.UpdateSpaceRequest;
import pe.edu.universidad.servicios.reservations.dto.ReservationDtos.UpdateSpaceTypeRequest;
import pe.edu.universidad.servicios.reservations.service.SpaceAdminService;
import pe.edu.universidad.servicios.reservations.service.SpaceScheduleService;
import pe.edu.universidad.servicios.reservations.service.SpaceTypeAdminService;
import pe.edu.universidad.servicios.shared.dto.PageQuery;
import pe.edu.universidad.servicios.shared.dto.PageResponse;
import pe.edu.universidad.servicios.shared.security.AuthenticatedActor;
import pe.edu.universidad.servicios.shared.security.CurrentActor;

/**
 * Administración de espacios, tipos, horarios y política de reservas (HU-25).
 */
@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Administración de ambientes", description = "HU-25")
public class AdminSpaceController {

    private final SpaceAdminService spaces;
    private final SpaceTypeAdminService types;
    private final SpaceScheduleService schedules;

    public AdminSpaceController(SpaceAdminService spaces, SpaceTypeAdminService types, SpaceScheduleService schedules) {
        this.spaces = spaces;
        this.types = types;
        this.schedules = schedules;
    }

    @GetMapping("/spaces")
    @Operation(summary = "Lista espacios activos e inactivos")
    public PageResponse<SpaceResponse> listSpaces(@CurrentActor AuthenticatedActor actor,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return spaces.list(actor, PageQuery.of(page, size));
    }

    @PostMapping("/spaces")
    @Operation(summary = "Crea un ambiente")
    public ResponseEntity<SpaceResponse> createSpace(@CurrentActor AuthenticatedActor actor, @Valid @RequestBody CreateSpaceRequest body) {
        SpaceResponse created = spaces.create(actor, body);
        return ResponseEntity.created(URI.create("/api/v1/admin/spaces/" + created.id())).body(created);
    }

    @PatchMapping("/spaces/{id}")
    @Operation(summary = "Edita o desactiva un ambiente")
    public SpaceResponse updateSpace(@CurrentActor AuthenticatedActor actor, @PathVariable long id,
            @Valid @RequestBody UpdateSpaceRequest body) {
        return spaces.update(actor, id, body);
    }

    @GetMapping("/space-types")
    @Operation(summary = "Lista tipos de ambiente")
    public PageResponse<SpaceTypeResponse> listTypes(@CurrentActor AuthenticatedActor actor,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return types.list(actor, PageQuery.of(page, size));
    }

    @PostMapping("/space-types")
    @Operation(summary = "Crea un tipo de ambiente")
    public ResponseEntity<SpaceTypeResponse> createType(@CurrentActor AuthenticatedActor actor,
            @Valid @RequestBody CreateSpaceTypeRequest body) {
        SpaceTypeResponse created = types.create(actor, body);
        return ResponseEntity.created(URI.create("/api/v1/admin/space-types/" + created.id())).body(created);
    }

    @PatchMapping("/space-types/{id}")
    @Operation(summary = "Edita o desactiva un tipo de ambiente")
    public SpaceTypeResponse updateType(@CurrentActor AuthenticatedActor actor, @PathVariable long id,
            @Valid @RequestBody UpdateSpaceTypeRequest body) {
        return types.update(actor, id, body);
    }

    @GetMapping("/spaces/{id}/schedule")
    @Operation(summary = "Consulta el horario semanal del ambiente")
    public SpaceScheduleResponse schedule(@CurrentActor AuthenticatedActor actor, @PathVariable long id) {
        return schedules.schedule(actor, id);
    }

    @PutMapping("/spaces/{id}/schedule")
    @Operation(summary = "Sustituye el horario semanal completo")
    public SpaceScheduleResponse replaceSchedule(@CurrentActor AuthenticatedActor actor, @PathVariable long id,
            @Valid @RequestBody ReplaceSpaceScheduleRequest body) {
        return schedules.replaceSchedule(actor, id, body);
    }

    @GetMapping("/reservation-policy")
    @Operation(summary = "Consulta la política vigente de reservas")
    public ReservationPolicyResponse policy(@CurrentActor AuthenticatedActor actor) {
        return schedules.policy(actor);
    }

    @PutMapping("/reservation-policy")
    @Operation(summary = "Sustituye la política vigente para nuevas reservas")
    public ReservationPolicyResponse replacePolicy(@CurrentActor AuthenticatedActor actor,
            @Valid @RequestBody ReservationPolicyRequest body) {
        return schedules.replacePolicy(actor, body);
    }
}
