package pe.edu.universidad.servicios.catalogs.service;

import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.universidad.servicios.audit.service.AuditEventService;
import pe.edu.universidad.servicios.catalogs.dto.CatalogDtos.CreatePriorityRequest;
import pe.edu.universidad.servicios.catalogs.dto.CatalogDtos.PriorityResponse;
import pe.edu.universidad.servicios.catalogs.dto.CatalogDtos.UpdatePriorityRequest;
import pe.edu.universidad.servicios.catalogs.repository.CatalogRecords.Priority;
import pe.edu.universidad.servicios.catalogs.repository.CategoryRepository;
import pe.edu.universidad.servicios.catalogs.repository.PriorityRepository;
import pe.edu.universidad.servicios.shared.dto.PageQuery;
import pe.edu.universidad.servicios.shared.dto.PageResponse;
import pe.edu.universidad.servicios.shared.error.ApiException;
import pe.edu.universidad.servicios.shared.security.AuthenticatedActor;
import pe.edu.universidad.servicios.shared.security.RoleCode;
import pe.edu.universidad.servicios.shared.web.Ids;
import pe.edu.universidad.servicios.shared.web.Patch;

@Service
public class PriorityCatalogService {

    private final PriorityRepository priorities;
    private final CategoryRepository categories;
    private final AuditEventService audit;

    public PriorityCatalogService(PriorityRepository priorities, CategoryRepository categories, AuditEventService audit) {
        this.priorities = priorities;
        this.categories = categories;
        this.audit = audit;
    }

    public Optional<Priority> find(long id) {
        return priorities.findById(id);
    }

    public Optional<Priority> findActive(long id) {
        return priorities.findById(id).filter(Priority::activo);
    }

    @Transactional(readOnly = true)
    public PageResponse<PriorityResponse> list(AuthenticatedActor actor, PageQuery page) {
        actor.requireRole(RoleCode.ADMINISTRADOR);
        return new PageResponse<>(priorities.page(page).stream().map(PriorityCatalogService::toResponse).toList(),
                page.page(), page.size(), priorities.count());
    }

    @Transactional
    public PriorityResponse create(AuthenticatedActor actor, CreatePriorityRequest body) {
        actor.requireRole(RoleCode.ADMINISTRADOR);
        try {
            long id = priorities.insert(body.codigo().trim(), body.nombre().trim(), body.nivel(), body.color());
            audit.record("prioridades", id, null, "CREAR", null, null, body.codigo().trim(), actor.userId());
            return toResponse(priorities.findById(id).orElseThrow());
        } catch (DuplicateKeyException ex) {
            throw ApiException.conflict("PRIORIDAD_DUPLICADA", "Ya existe una prioridad con ese código, nombre o nivel.");
        }
    }

    @Transactional
    public PriorityResponse update(AuthenticatedActor actor, long id, UpdatePriorityRequest body) {
        actor.requireRole(RoleCode.ADMINISTRADOR);
        Patch.rejectCodeChange(body.codigo());
        Patch.requireAny(body.nombre(), body.nivel(), body.color(), body.activo());
        Priority current = priorities.findById(id).orElseThrow(() -> ApiException.notFound("La prioridad no existe."));
        boolean activo = Patch.or(body.activo(), current.activo());
        if (current.activo() && !activo && categories.countActiveUsingPriority(id) > 0) {
            throw ApiException.conflict("DEPENDENCIAS_ACTIVAS",
                    "La prioridad es predeterminada de categorías activas. Cámbielas antes de desactivarla.");
        }
        String nombre = Patch.text("nombre", body.nombre(), current.nombre());
        int nivel = Patch.or(body.nivel(), current.nivel());
        String color = Patch.optionalText(body.color(), current.color());
        try {
            priorities.update(id, nombre, nivel, color, activo);
        } catch (DuplicateKeyException ex) {
            throw ApiException.conflict("PRIORIDAD_DUPLICADA", "Ya existe una prioridad con ese nombre o nivel.");
        }
        if (!nombre.equals(current.nombre())) {
            audit.record("prioridades", id, null, "ACTUALIZAR", "nombre", current.nombre(), nombre, actor.userId());
        }
        if (nivel != current.nivel()) {
            audit.record("prioridades", id, null, "ACTUALIZAR", "nivel", String.valueOf(current.nivel()), String.valueOf(nivel), actor.userId());
        }
        if (activo != current.activo()) {
            audit.record("prioridades", id, null, "ACTUALIZAR", "activo", String.valueOf(current.activo()), String.valueOf(activo), actor.userId());
        }
        return toResponse(priorities.findById(id).orElseThrow());
    }

    static PriorityResponse toResponse(Priority p) {
        return new PriorityResponse(Ids.str(p.id()), p.codigo(), p.nombre(), p.nivel(), p.color(), p.activo());
    }
}
