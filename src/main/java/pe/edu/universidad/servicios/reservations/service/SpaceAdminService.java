package pe.edu.universidad.servicios.reservations.service;

import java.util.Objects;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.universidad.servicios.audit.service.AuditEventService;
import pe.edu.universidad.servicios.catalogs.service.AreaCatalogService;
import pe.edu.universidad.servicios.reservations.dto.ReservationDtos.CreateSpaceRequest;
import pe.edu.universidad.servicios.reservations.dto.ReservationDtos.SpaceResponse;
import pe.edu.universidad.servicios.reservations.dto.ReservationDtos.UpdateSpaceRequest;
import pe.edu.universidad.servicios.reservations.repository.SpaceConfigRepository;
import pe.edu.universidad.servicios.reservations.repository.SpaceConfigRepository.SpaceType;
import pe.edu.universidad.servicios.reservations.repository.SpaceRepository;
import pe.edu.universidad.servicios.reservations.repository.SpaceRepository.Space;
import pe.edu.universidad.servicios.shared.dto.PageQuery;
import pe.edu.universidad.servicios.shared.dto.PageResponse;
import pe.edu.universidad.servicios.shared.error.ApiException;
import pe.edu.universidad.servicios.shared.security.AuthenticatedActor;
import pe.edu.universidad.servicios.shared.security.RoleCode;
import pe.edu.universidad.servicios.shared.web.Ids;
import pe.edu.universidad.servicios.shared.web.Patch;

/**
 * Administración de ambientes (HU-25). La baja lógica impide nuevas reservas y conserva las existentes.
 * La aprobación es obligatoria en este alcance: requiereAprobacion siempre es verdadero.
 */
@Service
public class SpaceAdminService {

    private final SpaceRepository spaces;
    private final SpaceConfigRepository config;
    private final AreaCatalogService areas;
    private final AuditEventService audit;

    public SpaceAdminService(SpaceRepository spaces, SpaceConfigRepository config, AreaCatalogService areas,
            AuditEventService audit) {
        this.spaces = spaces;
        this.config = config;
        this.areas = areas;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public PageResponse<SpaceResponse> list(AuthenticatedActor actor, PageQuery page) {
        actor.requireRole(RoleCode.ADMINISTRADOR);
        return new PageResponse<>(spaces.page(page).stream().map(SpaceAdminService::toResponse).toList(),
                page.page(), page.size(), spaces.count());
    }

    @Transactional
    public SpaceResponse create(AuthenticatedActor actor, CreateSpaceRequest body) {
        actor.requireRole(RoleCode.ADMINISTRADOR);
        rejectOptionalApproval(body.requiereAprobacion());
        long tipoId = activeType(Ids.required("tipoEspacioId", body.tipoEspacioId()));
        Long areaId = activeAreaOrNull(Ids.parse("areaId", body.areaId()));
        try {
            long id = spaces.insert(tipoId, areaId, body.codigo().trim(), body.nombre().trim(),
                    Patch.trimToNull(body.descripcion()), body.pabellon() == null ? "" : body.pabellon().trim(),
                    Patch.trimToNull(body.piso()), Patch.trimToNull(body.ubicacionDetallada()), body.capacidad());
            audit.record("espacios", id, null, "CREAR", null, null, body.codigo().trim(), actor.userId());
            return toResponse(spaces.findById(id).orElseThrow());
        } catch (DuplicateKeyException ex) {
            throw duplicated();
        }
    }

    @Transactional
    public SpaceResponse update(AuthenticatedActor actor, long id, UpdateSpaceRequest body) {
        actor.requireRole(RoleCode.ADMINISTRADOR);
        Patch.rejectCodeChange(body.codigo());
        rejectOptionalApproval(body.requiereAprobacion());
        Patch.requireAny(body.nombre(), body.tipoEspacioId(), body.areaId(), body.pabellon(), body.piso(), body.descripcion(),
                body.ubicacionDetallada(), body.capacidad(), body.activo());
        Space current = spaces.lockById(id).orElseThrow(() -> ApiException.notFound("El espacio no existe."));
        Long requestedType = Ids.parse("tipoEspacioId", body.tipoEspacioId());
        long tipoId = requestedType != null && requestedType != current.tipoId() ? activeType(requestedType) : current.tipoId();
        Long areaId = current.areaId();
        if (body.areaId() != null) {
            Long requestedArea = Ids.parse("areaId", body.areaId());
            areaId = requestedArea == null || Objects.equals(requestedArea, current.areaId()) ? requestedArea : activeAreaOrNull(requestedArea);
        }
        Space updated = new Space(current.id(), tipoId, current.tipoNombre(), current.tipoActivo(), areaId, current.codigo(),
                Patch.text("nombre", body.nombre(), current.nombre()),
                Patch.optionalText(body.descripcion(), current.descripcion()),
                body.pabellon() != null ? body.pabellon().trim() : current.pabellon(),
                Patch.optionalText(body.piso(), current.piso()),
                Patch.optionalText(body.ubicacionDetallada(), current.ubicacionDetallada()),
                Patch.or(body.capacidad(), current.capacidad()), true, Patch.or(body.activo(), current.activo()));
        try {
            spaces.update(updated);
        } catch (DuplicateKeyException ex) {
            throw duplicated();
        }
        if (updated.activo() != current.activo()) {
            audit.record("espacios", id, null, "ACTUALIZAR", "activo", String.valueOf(current.activo()),
                    String.valueOf(updated.activo()), actor.userId());
        }
        if (!Objects.equals(updated.areaId(), current.areaId())) {
            audit.record("espacios", id, null, "ACTUALIZAR", "areaId", String.valueOf(current.areaId()),
                    String.valueOf(updated.areaId()), actor.userId());
        }
        return toResponse(spaces.findById(id).orElseThrow());
    }

    private long activeType(long tipoId) {
        return config.findType(tipoId).filter(SpaceType::activo)
                .orElseThrow(() -> ApiException.invalidField("tipoEspacioId", "El tipo de espacio no existe o no está activo."))
                .id();
    }

    private Long activeAreaOrNull(Long areaId) {
        if (areaId == null) {
            return null;
        }
        return areas.findActive(areaId)
                .orElseThrow(() -> ApiException.invalidField("areaId", "El área no existe o no está activa."))
                .id();
    }

    private static void rejectOptionalApproval(Boolean requiereAprobacion) {
        if (Boolean.FALSE.equals(requiereAprobacion)) {
            throw ApiException.invalidField("requiereAprobacion", "La aprobación es obligatoria para todos los espacios.");
        }
    }

    private static ApiException duplicated() {
        return ApiException.conflict("ESPACIO_DUPLICADO", "Ya existe un espacio con ese código o con ese nombre en el pabellón.");
    }

    static SpaceResponse toResponse(Space s) {
        return new SpaceResponse(Ids.str(s.id()), s.codigo(), s.nombre(), Ids.str(s.tipoId()), Ids.str(s.areaId()),
                s.pabellon(), s.piso(), s.descripcion(), s.ubicacionDetallada(), s.capacidad(), s.requiereAprobacion(), s.activo());
    }
}
