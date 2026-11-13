package pe.edu.universidad.servicios.catalogs.service;

import java.util.Objects;
import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.universidad.servicios.audit.service.AuditEventService;
import pe.edu.universidad.servicios.catalogs.dto.CatalogDtos.AvailableCategoryResponse;
import pe.edu.universidad.servicios.catalogs.dto.CatalogDtos.CategoryResponse;
import pe.edu.universidad.servicios.catalogs.dto.CatalogDtos.CreateCategoryRequest;
import pe.edu.universidad.servicios.catalogs.dto.CatalogDtos.UpdateCategoryRequest;
import pe.edu.universidad.servicios.catalogs.repository.CatalogRecords.Category;
import pe.edu.universidad.servicios.catalogs.repository.CategoryRepository;
import pe.edu.universidad.servicios.shared.dto.PageQuery;
import pe.edu.universidad.servicios.shared.dto.PageResponse;
import pe.edu.universidad.servicios.shared.error.ApiException;
import pe.edu.universidad.servicios.shared.security.AuthenticatedActor;
import pe.edu.universidad.servicios.shared.security.RoleCode;
import pe.edu.universidad.servicios.shared.web.Ids;
import pe.edu.universidad.servicios.shared.web.Patch;

@Service
public class CategoryCatalogService {

    private final CategoryRepository categories;
    private final AreaCatalogService areas;
    private final PriorityCatalogService priorities;
    private final AuditEventService audit;

    public CategoryCatalogService(CategoryRepository categories, AreaCatalogService areas,
            PriorityCatalogService priorities, AuditEventService audit) {
        this.categories = categories;
        this.areas = areas;
        this.priorities = priorities;
        this.audit = audit;
    }

    /** Contrato interno para el alta de solicitudes: categoría, área y prioridad predeterminada activas. */
    public Optional<Category> findAvailable(long id) {
        return categories.findAvailable(id);
    }

    public Optional<Category> find(long id) {
        return categories.findById(id);
    }

    @Transactional(readOnly = true)
    public PageResponse<AvailableCategoryResponse> listAvailable(PageQuery page) {
        return new PageResponse<>(categories.availablePage(page).stream()
                .map(c -> new AvailableCategoryResponse(Ids.str(c.id()), c.nombre(), Ids.str(c.areaId())))
                .toList(), page.page(), page.size(), categories.countAvailable());
    }

    @Transactional(readOnly = true)
    public PageResponse<CategoryResponse> list(AuthenticatedActor actor, PageQuery page) {
        actor.requireRole(RoleCode.ADMINISTRADOR);
        return new PageResponse<>(categories.page(page).stream().map(CategoryCatalogService::toResponse).toList(),
                page.page(), page.size(), categories.count());
    }

    @Transactional
    public CategoryResponse create(AuthenticatedActor actor, CreateCategoryRequest body) {
        actor.requireRole(RoleCode.ADMINISTRADOR);
        long areaId = activeArea(Ids.required("areaId", body.areaId()));
        long prioridadId = activePriority(Ids.required("prioridadPredeterminadaId", body.prioridadPredeterminadaId()));
        try {
            long id = categories.insert(body.codigo().trim(), body.nombre().trim(), Patch.trimToNull(body.descripcion()),
                    areaId, prioridadId);
            audit.record("categorias", id, null, "CREAR", null, null, body.codigo().trim(), actor.userId());
            return toResponse(categories.findById(id).orElseThrow());
        } catch (DuplicateKeyException ex) {
            throw ApiException.conflict("CATEGORIA_DUPLICADA", "Ya existe una categoría con ese código o nombre.");
        }
    }

    @Transactional
    public CategoryResponse update(AuthenticatedActor actor, long id, UpdateCategoryRequest body) {
        actor.requireRole(RoleCode.ADMINISTRADOR);
        Patch.rejectCodeChange(body.codigo());
        Patch.requireAny(body.nombre(), body.descripcion(), body.areaId(), body.prioridadPredeterminadaId(), body.activo());
        Category current = categories.findById(id).orElseThrow(() -> ApiException.notFound("La categoría no existe."));
        Long requestedArea = Ids.parse("areaId", body.areaId());
        Long requestedPriority = Ids.parse("prioridadPredeterminadaId", body.prioridadPredeterminadaId());
        // Las referencias nuevas deben estar activas; las existentes se conservan aunque se hayan desactivado.
        long areaId = requestedArea != null && requestedArea != current.areaId() ? activeArea(requestedArea) : current.areaId();
        long prioridadId = requestedPriority != null && requestedPriority != current.prioridadPredeterminadaId()
                ? activePriority(requestedPriority) : current.prioridadPredeterminadaId();
        String nombre = Patch.text("nombre", body.nombre(), current.nombre());
        String descripcion = Patch.optionalText(body.descripcion(), current.descripcion());
        boolean activo = Patch.or(body.activo(), current.activo());
        try {
            categories.update(id, nombre, descripcion, areaId, prioridadId, activo);
        } catch (DuplicateKeyException ex) {
            throw ApiException.conflict("CATEGORIA_DUPLICADA", "Ya existe una categoría con ese nombre.");
        }
        auditChange(actor, id, "nombre", current.nombre(), nombre);
        auditChange(actor, id, "areaId", String.valueOf(current.areaId()), String.valueOf(areaId));
        auditChange(actor, id, "prioridadPredeterminadaId", String.valueOf(current.prioridadPredeterminadaId()), String.valueOf(prioridadId));
        auditChange(actor, id, "activo", String.valueOf(current.activo()), String.valueOf(activo));
        return toResponse(categories.findById(id).orElseThrow());
    }

    private void auditChange(AuthenticatedActor actor, long id, String campo, String anterior, String nuevo) {
        if (!Objects.equals(anterior, nuevo)) {
            audit.record("categorias", id, null, "ACTUALIZAR", campo, anterior, nuevo, actor.userId());
        }
    }

    private long activeArea(long id) {
        return areas.findActive(id)
                .orElseThrow(() -> ApiException.invalidField("areaId", "El área no existe o no está activa."))
                .id();
    }

    private long activePriority(long id) {
        return priorities.findActive(id)
                .orElseThrow(() -> ApiException.invalidField("prioridadPredeterminadaId", "La prioridad no existe o no está activa."))
                .id();
    }

    static CategoryResponse toResponse(Category c) {
        return new CategoryResponse(Ids.str(c.id()), c.codigo(), c.nombre(), c.descripcion(), Ids.str(c.areaId()),
                Ids.str(c.prioridadPredeterminadaId()), c.activo());
    }
}
