package pe.edu.universidad.servicios.sla.controller;

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
import pe.edu.universidad.servicios.shared.dto.PageQuery;
import pe.edu.universidad.servicios.shared.dto.PageResponse;
import pe.edu.universidad.servicios.shared.security.AuthenticatedActor;
import pe.edu.universidad.servicios.shared.security.CurrentActor;
import pe.edu.universidad.servicios.sla.dto.SlaDtos.CreateSlaPolicyRequest;
import pe.edu.universidad.servicios.sla.dto.SlaDtos.SlaPolicyResponse;
import pe.edu.universidad.servicios.sla.dto.SlaDtos.UpdateSlaPolicyRequest;
import pe.edu.universidad.servicios.sla.service.SlaPolicyService;

@RestController
@RequestMapping("/api/v1/admin/sla-policies")
@Tag(name = "SLA, auditoría y satisfacción", description = "HU-18 a HU-20")
public class AdminSlaPolicyController {

    private final SlaPolicyService policies;

    public AdminSlaPolicyController(SlaPolicyService policies) {
        this.policies = policies;
    }

    @GetMapping
    @Operation(summary = "Consulta las políticas de SLA")
    public PageResponse<SlaPolicyResponse> list(@CurrentActor AuthenticatedActor actor,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return policies.list(actor, PageQuery.of(page, size));
    }

    @PostMapping
    @Operation(summary = "Define una política para solicitudes nuevas")
    public ResponseEntity<SlaPolicyResponse> create(@CurrentActor AuthenticatedActor actor,
            @Valid @RequestBody CreateSlaPolicyRequest body) {
        SlaPolicyResponse created = policies.create(actor, body);
        return ResponseEntity.created(URI.create("/api/v1/admin/sla-policies/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Ajusta o desactiva una política para solicitudes nuevas")
    public SlaPolicyResponse update(@CurrentActor AuthenticatedActor actor, @PathVariable long id,
            @Valid @RequestBody UpdateSlaPolicyRequest body) {
        return policies.update(actor, id, body);
    }
}
