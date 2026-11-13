package pe.edu.universidad.servicios.management.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import pe.edu.universidad.servicios.management.dto.ManagementDtos.AssignTechnicianRequest;
import pe.edu.universidad.servicios.management.dto.ManagementDtos.AssignmentResponse;
import pe.edu.universidad.servicios.management.dto.ManagementDtos.ChangePriorityRequest;
import pe.edu.universidad.servicios.management.dto.ManagementDtos.ChangeRequestStatusRequest;
import pe.edu.universidad.servicios.management.service.RequestManagementService;
import pe.edu.universidad.servicios.requests.dto.RequestDtos.RequestSummaryResponse;
import pe.edu.universidad.servicios.shared.dto.PageQuery;
import pe.edu.universidad.servicios.shared.dto.PageResponse;
import pe.edu.universidad.servicios.shared.security.AuthenticatedActor;
import pe.edu.universidad.servicios.shared.security.CurrentActor;
import pe.edu.universidad.servicios.users.dto.UserDtos.EligibleTechnicianResponse;

@RestController
@RequestMapping("/api/v1/requests/{id}")
@Tag(name = "Asignación, prioridad y ciclo de vida", description = "HU-07 a HU-11")
public class RequestManagementController {

    private final RequestManagementService management;

    public RequestManagementController(RequestManagementService management) {
        this.management = management;
    }

    @GetMapping("/eligible-technicians")
    @Operation(summary = "Lista técnicos activos del área de la solicitud")
    public PageResponse<EligibleTechnicianResponse> eligibleTechnicians(@CurrentActor AuthenticatedActor actor,
            @PathVariable long id, @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return management.eligibleTechnicians(actor, id, PageQuery.of(page, size));
    }

    @PostMapping("/assignments")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Asigna o reasigna la solicitud")
    public AssignmentResponse assign(@CurrentActor AuthenticatedActor actor, @PathVariable long id,
            @Valid @RequestBody AssignTechnicianRequest body) {
        return management.assign(actor, id, body);
    }

    @PatchMapping
    @Operation(summary = "Actualiza la prioridad de una solicitud abierta")
    public RequestSummaryResponse changePriority(@CurrentActor AuthenticatedActor actor, @PathVariable long id,
            @Valid @RequestBody ChangePriorityRequest body) {
        return management.changePriority(actor, id, body);
    }

    @PatchMapping("/status")
    @Operation(summary = "Cambia el estado según la transición autorizada")
    public RequestSummaryResponse changeStatus(@CurrentActor AuthenticatedActor actor, @PathVariable long id,
            @Valid @RequestBody ChangeRequestStatusRequest body) {
        return management.changeStatus(actor, id, body);
    }
}
