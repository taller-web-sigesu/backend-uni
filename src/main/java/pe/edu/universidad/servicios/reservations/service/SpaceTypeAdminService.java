package pe.edu.universidad.servicios.reservations.service;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.universidad.servicios.audit.service.AuditEventService;
import pe.edu.universidad.servicios.reservations.dto.ReservationDtos.CreateSpaceTypeRequest;
import pe.edu.universidad.servicios.reservations.dto.ReservationDtos.SpaceTypeResponse;
import pe.edu.universidad.servicios.reservations.dto.ReservationDtos.UpdateSpaceTypeRequest;
import pe.edu.universidad.servicios.reservations.repository.SpaceConfigRepository;
import pe.edu.universidad.servicios.reservations.repository.SpaceConfigRepository.SpaceType;
import pe.edu.universidad.servicios.shared.dto.PageQuery;
import pe.edu.universidad.servicios.shared.dto.PageResponse;
import pe.edu.universidad.servicios.shared.error.ApiException;
import pe.edu.universidad.servicios.shared.security.AuthenticatedActor;
import pe.edu.universidad.servicios.shared.security.RoleCode;
import pe.edu.universidad.servicios.shared.web.Ids;
import pe.edu.universidad.servicios.shared.web.Patch;

@Service
public class SpaceTypeAdminService {

    private final SpaceConfigRepository config;
    private final AuditEventService audit;

    public SpaceTypeAdminService(SpaceConfigRepository config, AuditEventService audit) {
        this.config = config;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public PageResponse<SpaceTypeResponse> list(AuthenticatedActor actor, PageQuery page) {
        actor.requireRole(RoleCode.ADMINISTRADOR);
        return new PageResponse<>(config.typePage(page).stream().map(SpaceTypeAdminService::toResponse).toList(),
                page.page(), page.size(), config.countTypes());
    }

    @Transactional
    public SpaceTypeResponse create(AuthenticatedActor actor, CreateSpaceTypeRequest body) {
        actor.requireRole(RoleCode.ADMINISTRADOR);
        try {
            long id = config.insertType(body.codigo().trim(), body.nombre().trim(), Patch.trimToNull(body.descripcion()));
            audit.record("tipos_espacio", id, null, "CREAR", null, null, body.codigo().trim(), actor.userId());
            return toResponse(config.findType(id).orElseThrow());
        } catch (DuplicateKeyException ex) {
            throw duplicated();
        }
    }

    @Transactional
    public SpaceTypeResponse update(AuthenticatedActor actor, long id, UpdateSpaceTypeRequest body) {
        actor.requireRole(RoleCode.ADMINISTRADOR);
        Patch.rejectCodeChange(body.codigo());
        Patch.requireAny(body.nombre(), body.descripcion(), body.activo());
        SpaceType current = config.findType(id).orElseThrow(() -> ApiException.notFound("El tipo de espacio no existe."));
        String nombre = Patch.text("nombre", body.nombre(), current.nombre());
        boolean activo = Patch.or(body.activo(), current.activo());
        try {
            config.updateType(id, nombre, Patch.optionalText(body.descripcion(), current.descripcion()), activo);
        } catch (DuplicateKeyException ex) {
            throw duplicated();
        }
        if (activo != current.activo()) {
            audit.record("tipos_espacio", id, null, "ACTUALIZAR", "activo", String.valueOf(current.activo()),
                    String.valueOf(activo), actor.userId());
        }
        return toResponse(config.findType(id).orElseThrow());
    }

    private static ApiException duplicated() {
        return ApiException.conflict("TIPO_DUPLICADO", "Ya existe un tipo de espacio con ese código o nombre.");
    }

    static SpaceTypeResponse toResponse(SpaceType t) {
        return new SpaceTypeResponse(Ids.str(t.id()), t.codigo(), t.nombre(), t.descripcion(), t.activo());
    }
}
