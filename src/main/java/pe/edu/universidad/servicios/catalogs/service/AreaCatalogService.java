package pe.edu.universidad.servicios.catalogs.service;

import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.universidad.servicios.audit.service.AuditEventService;
import pe.edu.universidad.servicios.catalogs.dto.CatalogDtos.AreaResponse;
import pe.edu.universidad.servicios.catalogs.dto.CatalogDtos.CreateAreaRequest;
import pe.edu.universidad.servicios.catalogs.dto.CatalogDtos.UpdateAreaRequest;
import pe.edu.universidad.servicios.catalogs.repository.AreaRepository;
import pe.edu.universidad.servicios.catalogs.repository.CatalogRecords.Area;
import pe.edu.universidad.servicios.shared.dto.PageQuery;
import pe.edu.universidad.servicios.shared.dto.PageResponse;
import pe.edu.universidad.servicios.shared.error.ApiException;
import pe.edu.universidad.servicios.shared.security.AuthenticatedActor;
import pe.edu.universidad.servicios.shared.security.RoleCode;
import pe.edu.universidad.servicios.shared.web.Ids;
import pe.edu.universidad.servicios.shared.web.Patch;
import pe.edu.universidad.servicios.users.repository.UserRepository;

@Service
public class AreaCatalogService {

    private final AreaRepository areas;
    private final UserRepository users;
    private final AuditEventService audit;

    public AreaCatalogService(AreaRepository areas, UserRepository users, AuditEventService audit) {
        this.areas = areas;
        this.users = users;
        this.audit = audit;
    }

    /** Contrato interno: área existente y activa. */
    public Optional<Area> findActive(long id) {
        return areas.findById(id).filter(Area::activo);
    }

    public Optional<Area> find(long id) {
        return areas.findById(id);
    }

    @Transactional(readOnly = true)
    public PageResponse<AreaResponse> list(AuthenticatedActor actor, PageQuery page) {
        actor.requireRole(RoleCode.ADMINISTRADOR);
        return new PageResponse<>(areas.page(page).stream().map(AreaCatalogService::toResponse).toList(),
                page.page(), page.size(), areas.count());
    }

    @Transactional
    public AreaResponse create(AuthenticatedActor actor, CreateAreaRequest body) {
        actor.requireRole(RoleCode.ADMINISTRADOR);
        try {
            long id = areas.insert(body.codigo().trim(), body.nombre().trim(), Patch.trimToNull(body.descripcion()));
            audit.record("areas", id, null, "CREAR", null, null, body.codigo().trim(), actor.userId());
            return toResponse(areas.findById(id).orElseThrow());
        } catch (DuplicateKeyException ex) {
            throw ApiException.conflict("AREA_DUPLICADA", "Ya existe un área con ese código o nombre.");
        }
    }

    @Transactional
    public AreaResponse update(AuthenticatedActor actor, long id, UpdateAreaRequest body) {
        actor.requireRole(RoleCode.ADMINISTRADOR);
        Patch.rejectCodeChange(body.codigo());
        Patch.requireAny(body.nombre(), body.descripcion(), body.activo());
        Area current = areas.findById(id).orElseThrow(() -> ApiException.notFound("El área no existe."));
        boolean activo = Patch.or(body.activo(), current.activo());
        if (current.activo() && !activo) {
            if (areas.countActiveCategories(id) > 0 || users.countActiveStaffInArea(id) > 0) {
                throw ApiException.conflict("DEPENDENCIAS_ACTIVAS",
                        "El área tiene categorías o personal activos. Reasígnelos antes de desactivarla.");
            }
        }
        String nombre = Patch.text("nombre", body.nombre(), current.nombre());
        String descripcion = Patch.optionalText(body.descripcion(), current.descripcion());
        try {
            areas.update(id, nombre, descripcion, activo);
        } catch (DuplicateKeyException ex) {
            throw ApiException.conflict("AREA_DUPLICADA", "Ya existe un área con ese nombre.");
        }
        if (!nombre.equals(current.nombre())) {
            audit.record("areas", id, null, "ACTUALIZAR", "nombre", current.nombre(), nombre, actor.userId());
        }
        if (activo != current.activo()) {
            audit.record("areas", id, null, "ACTUALIZAR", "activo", String.valueOf(current.activo()), String.valueOf(activo), actor.userId());
        }
        return toResponse(areas.findById(id).orElseThrow());
    }

    static AreaResponse toResponse(Area a) {
        return new AreaResponse(Ids.str(a.id()), a.codigo(), a.nombre(), a.descripcion(), a.activo());
    }
}
