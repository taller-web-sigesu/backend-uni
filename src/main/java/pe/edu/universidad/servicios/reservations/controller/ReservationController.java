package pe.edu.universidad.servicios.reservations.controller;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import pe.edu.universidad.servicios.reservations.dto.ReservationDtos.ChangeReservationStatusRequest;
import pe.edu.universidad.servicios.reservations.dto.ReservationDtos.CreateReservationRequest;
import pe.edu.universidad.servicios.reservations.dto.ReservationDtos.ReservationDetailResponse;
import pe.edu.universidad.servicios.reservations.dto.ReservationDtos.ReservationHistoryResponse;
import pe.edu.universidad.servicios.reservations.dto.ReservationDtos.ReservationSummaryResponse;
import pe.edu.universidad.servicios.reservations.service.ReservationDecisionService;
import pe.edu.universidad.servicios.reservations.service.ReservationQueryService;
import pe.edu.universidad.servicios.reservations.service.ReservationService;
import pe.edu.universidad.servicios.shared.dto.PageQuery;
import pe.edu.universidad.servicios.shared.dto.PageResponse;
import pe.edu.universidad.servicios.shared.security.AuthenticatedActor;
import pe.edu.universidad.servicios.shared.security.CurrentActor;

@RestController
@RequestMapping("/api/v1/reservations")
@Tag(name = "Reserva de ambientes", description = "HU-21 a HU-25")
public class ReservationController {

    private final ReservationService reservations;
    private final ReservationQueryService queries;
    private final ReservationDecisionService decisions;

    public ReservationController(ReservationService reservations, ReservationQueryService queries,
            ReservationDecisionService decisions) {
        this.reservations = reservations;
        this.queries = queries;
        this.decisions = decisions;
    }

    @PostMapping
    @Operation(summary = "Registra una reserva pendiente de aprobación")
    public ResponseEntity<ReservationSummaryResponse> create(@CurrentActor AuthenticatedActor actor,
            @Valid @RequestBody CreateReservationRequest body) {
        ReservationSummaryResponse created = reservations.create(actor, body);
        return ResponseEntity.created(URI.create("/api/v1/reservations/" + created.id())).body(created);
    }

    @GetMapping
    @Operation(summary = "Lista reservas propias o la bandeja de aprobación (vista=gestion)")
    public PageResponse<ReservationSummaryResponse> list(@CurrentActor AuthenticatedActor actor,
            @RequestParam(required = false) String vista, @RequestParam(required = false) String estado,
            @RequestParam(required = false) String fechaDesde, @RequestParam(required = false) String fechaHasta,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return queries.list(actor, vista, estado, fechaDesde, fechaHasta, PageQuery.of(page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consulta una reserva y su estado operativo")
    public ReservationDetailResponse detail(@CurrentActor AuthenticatedActor actor, @PathVariable long id) {
        return queries.detail(actor, id);
    }

    @GetMapping("/{id}/history")
    @Operation(summary = "Consulta las transiciones de una reserva")
    public PageResponse<ReservationHistoryResponse> history(@CurrentActor AuthenticatedActor actor, @PathVariable long id,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return queries.history(actor, id, PageQuery.of(page, size));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Confirma, rechaza o cancela una reserva")
    public ReservationDetailResponse changeStatus(@CurrentActor AuthenticatedActor actor, @PathVariable long id,
            @Valid @RequestBody ChangeReservationStatusRequest body) {
        return decisions.changeStatus(actor, id, body);
    }
}
