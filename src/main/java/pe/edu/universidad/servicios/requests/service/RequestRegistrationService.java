package pe.edu.universidad.servicios.requests.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.universidad.servicios.audit.service.AuditEventService;
import pe.edu.universidad.servicios.catalogs.repository.CatalogRecords.Category;
import pe.edu.universidad.servicios.catalogs.service.CategoryCatalogService;
import pe.edu.universidad.servicios.catalogs.service.RequestStatusCatalogService;
import pe.edu.universidad.servicios.requests.dto.RequestDtos.CreateRequestRequest;
import pe.edu.universidad.servicios.requests.dto.RequestDtos.RequestSummaryResponse;
import pe.edu.universidad.servicios.requests.repository.RequestActivityRepository;
import pe.edu.universidad.servicios.requests.repository.ServiceRequestRepository;
import pe.edu.universidad.servicios.shared.error.ApiException;
import pe.edu.universidad.servicios.shared.security.AuthenticatedActor;
import pe.edu.universidad.servicios.shared.security.RoleCode;
import pe.edu.universidad.servicios.shared.web.Ids;
import pe.edu.universidad.servicios.sla.service.SlaResolutionService;
import pe.edu.universidad.servicios.sla.service.SlaResolutionService.SlaDeadline;

/**
 * Alta de solicitudes (HU-03): código, SLA congelado, historial inicial y auditoría en una transacción.
 */
@Service
public class RequestRegistrationService {

    private final ServiceRequestRepository requests;
    private final RequestActivityRepository activity;
    private final CategoryCatalogService categories;
    private final RequestStatusCatalogService statuses;
    private final SlaResolutionService sla;
    private final AuditEventService audit;
    private final Clock clock;

    public RequestRegistrationService(ServiceRequestRepository requests, RequestActivityRepository activity,
            CategoryCatalogService categories, RequestStatusCatalogService statuses, SlaResolutionService sla,
            AuditEventService audit, Clock clock) {
        this.requests = requests;
        this.activity = activity;
        this.categories = categories;
        this.statuses = statuses;
        this.sla = sla;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public RequestSummaryResponse create(AuthenticatedActor actor, CreateRequestRequest body) {
        actor.requireRole(RoleCode.ESTUDIANTE, RoleCode.DOCENTE);
        long categoriaId = Ids.required("categoriaId", body.categoriaId());
        Category category = categories.findAvailable(categoriaId)
                .orElseThrow(() -> ApiException.invalidField("categoriaId", "La categoría no existe o no está habilitada."));
        String ubicacion = body.ubicacion().trim();
        String descripcion = body.descripcion().trim();
        Instant now = clock.instant();
        long estadoNuevo = statuses.requireByCode("NUEVO").id();
        Optional<SlaDeadline> deadline = sla.resolve(category.id(), category.prioridadPredeterminadaId(), now);
        String codigo = requests.nextCode(now.atZone(ZoneOffset.UTC).getYear());
        long id = requests.insert(codigo, actor.userId(), category.id(), category.areaId(), category.prioridadPredeterminadaId(),
                estadoNuevo, deadline.map(SlaDeadline::policyId).orElse(null), ubicacion, descripcion,
                deadline.map(SlaDeadline::fechaLimite).orElse(null), deadline.map(SlaDeadline::fechaAviso).orElse(null), now);
        activity.insertHistory(id, null, estadoNuevo, actor.userId(), null, now);
        audit.record("solicitudes", id, id, "CREAR", "estado", null, "NUEVO", actor.userId());
        return RequestMapper.summary(requests.findById(id).orElseThrow());
    }
}
