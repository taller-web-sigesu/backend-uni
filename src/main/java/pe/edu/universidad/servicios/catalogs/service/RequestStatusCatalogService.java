package pe.edu.universidad.servicios.catalogs.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.universidad.servicios.audit.service.AuditEventService;
import pe.edu.universidad.servicios.catalogs.dto.CatalogDtos.RenameRequestStatusRequest;
import pe.edu.universidad.servicios.catalogs.dto.CatalogDtos.RequestStatusResponse;
import pe.edu.universidad.servicios.catalogs.repository.CatalogRecords.RequestStatus;
import pe.edu.universidad.servicios.catalogs.repository.CatalogRecords.Transition;
import pe.edu.universidad.servicios.catalogs.repository.RequestStatusRepository;
import pe.edu.universidad.servicios.shared.error.ApiException;
import pe.edu.universidad.servicios.shared.security.AuthenticatedActor;
import pe.edu.universidad.servicios.shared.security.RoleCode;
import pe.edu.universidad.servicios.shared.web.Ids;
import pe.edu.universidad.servicios.shared.web.Patch;

@Service
public class RequestStatusCatalogService {

    private final RequestStatusRepository statuses;
    private final AuditEventService audit;

    public RequestStatusCatalogService(RequestStatusRepository statuses, AuditEventService audit) {
        this.statuses = statuses;
        this.audit = audit;
    }

    public Optional<RequestStatus> findByCode(String codigo) {
        return statuses.findByCode(codigo);
    }

    public RequestStatus requireByCode(String codigo) {
        return statuses.findByCode(codigo).orElseThrow(() -> new IllegalStateException("Estado no sembrado: " + codigo));
    }

    /** Transiciones activas sembradas por Flyway (origen, destino, rol, motivo). */
    public List<Transition> activeTransitions() {
        return statuses.activeTransitions();
    }

    @Transactional(readOnly = true)
    public List<RequestStatusResponse> list(AuthenticatedActor actor) {
        actor.requireRole(RoleCode.ADMINISTRADOR);
        return statuses.findAll().stream().map(RequestStatusCatalogService::toResponse).toList();
    }

    @Transactional
    public RequestStatusResponse rename(AuthenticatedActor actor, long id, RenameRequestStatusRequest body) {
        actor.requireRole(RoleCode.ADMINISTRADOR);
        Patch.rejectCodeChange(body.codigo());
        if (body.nombre() == null || body.nombre().isBlank()) {
            throw ApiException.invalidField("nombre", "nombre es obligatorio.");
        }
        RequestStatus current = statuses.findById(id).orElseThrow(() -> ApiException.notFound("El estado no existe."));
        String nombre = body.nombre().trim();
        statuses.rename(id, nombre);
        if (!nombre.equals(current.nombre())) {
            audit.record("estados_solicitud", id, null, "ACTUALIZAR", "nombre", current.nombre(), nombre, actor.userId());
        }
        return toResponse(statuses.findById(id).orElseThrow());
    }

    static RequestStatusResponse toResponse(RequestStatus s) {
        return new RequestStatusResponse(Ids.str(s.id()), s.codigo(), s.nombre(), s.esFinal());
    }
}
