package pe.edu.universidad.servicios.catalogs.controller;

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
import pe.edu.universidad.servicios.catalogs.dto.CatalogDtos.CreatePriorityRequest;
import pe.edu.universidad.servicios.catalogs.dto.CatalogDtos.PriorityResponse;
import pe.edu.universidad.servicios.catalogs.dto.CatalogDtos.UpdatePriorityRequest;
import pe.edu.universidad.servicios.catalogs.service.PriorityCatalogService;
import pe.edu.universidad.servicios.shared.dto.PageQuery;
import pe.edu.universidad.servicios.shared.dto.PageResponse;
import pe.edu.universidad.servicios.shared.security.AuthenticatedActor;
import pe.edu.universidad.servicios.shared.security.CurrentActor;

@RestController
@RequestMapping("/api/v1/admin/catalogs/priorities")
@Tag(name = "Administración de catálogos", description = "HU-16 y HU-17")
public class AdminPriorityController {

    private final PriorityCatalogService priorities;

    public AdminPriorityController(PriorityCatalogService priorities) {
        this.priorities = priorities;
    }

    @GetMapping
    @Operation(summary = "Lista prioridades activas e inactivas")
    public PageResponse<PriorityResponse> list(@CurrentActor AuthenticatedActor actor,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return priorities.list(actor, PageQuery.of(page, size));
    }

    @PostMapping
    @Operation(summary = "Crea una prioridad")
    public ResponseEntity<PriorityResponse> create(@CurrentActor AuthenticatedActor actor,
            @Valid @RequestBody CreatePriorityRequest body) {
        PriorityResponse created = priorities.create(actor, body);
        return ResponseEntity.created(URI.create("/api/v1/admin/catalogs/priorities/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Edita o desactiva una prioridad")
    public PriorityResponse update(@CurrentActor AuthenticatedActor actor, @PathVariable long id,
            @Valid @RequestBody UpdatePriorityRequest body) {
        return priorities.update(actor, id, body);
    }
}
