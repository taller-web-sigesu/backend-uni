package pe.edu.universidad.servicios.catalogs.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import pe.edu.universidad.servicios.catalogs.dto.CatalogDtos.AvailableCategoryResponse;
import pe.edu.universidad.servicios.catalogs.service.CategoryCatalogService;
import pe.edu.universidad.servicios.shared.dto.PageQuery;
import pe.edu.universidad.servicios.shared.dto.PageResponse;
import pe.edu.universidad.servicios.shared.security.AuthenticatedActor;
import pe.edu.universidad.servicios.shared.security.CurrentActor;

@RestController
@RequestMapping("/api/v1/catalogs")
@Tag(name = "Catálogos", description = "HU-03")
public class CategoryCatalogController {

    private final CategoryCatalogService categories;

    public CategoryCatalogController(CategoryCatalogService categories) {
        this.categories = categories;
    }

    @GetMapping("/categories")
    @Operation(summary = "Lista categorías habilitadas para registrar solicitudes")
    public PageResponse<AvailableCategoryResponse> listAvailable(@CurrentActor AuthenticatedActor actor,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return categories.listAvailable(PageQuery.of(page, size));
    }
}
