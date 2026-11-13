package pe.edu.universidad.servicios.catalogs.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import pe.edu.universidad.servicios.catalogs.dto.CatalogDtos.RenameRequestStatusRequest;
import pe.edu.universidad.servicios.catalogs.dto.CatalogDtos.RequestStatusResponse;
import pe.edu.universidad.servicios.catalogs.service.RequestStatusCatalogService;
import pe.edu.universidad.servicios.shared.security.AuthenticatedActor;
import pe.edu.universidad.servicios.shared.security.CurrentActor;

@RestController
@RequestMapping("/api/v1/admin/catalogs/request-statuses")
@Tag(name = "Administración de catálogos", description = "HU-16 y HU-17")
public class AdminRequestStatusController {

    private final RequestStatusCatalogService statuses;

    public AdminRequestStatusController(RequestStatusCatalogService statuses) {
        this.statuses = statuses;
    }

    @GetMapping
    @Operation(summary = "Consulta los estados del flujo de solicitudes")
    public List<RequestStatusResponse> list(@CurrentActor AuthenticatedActor actor) {
        return statuses.list(actor);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Personaliza el nombre visible de un estado")
    public RequestStatusResponse rename(@CurrentActor AuthenticatedActor actor, @PathVariable long id,
            @Valid @RequestBody RenameRequestStatusRequest body) {
        return statuses.rename(actor, id, body);
    }
}
