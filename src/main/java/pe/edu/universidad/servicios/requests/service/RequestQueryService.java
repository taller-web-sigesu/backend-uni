package pe.edu.universidad.servicios.requests.service;

import java.time.Clock;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.universidad.servicios.audit.service.AuditQueryService;
import pe.edu.universidad.servicios.catalogs.service.RequestStatusCatalogService;
import pe.edu.universidad.servicios.requests.dto.RequestDtos.CommentResponse;
import pe.edu.universidad.servicios.requests.dto.RequestDtos.PersonResponse;
import pe.edu.universidad.servicios.requests.dto.RequestDtos.RequestAuditEventResponse;
import pe.edu.universidad.servicios.requests.dto.RequestDtos.RequestDetailResponse;
import pe.edu.universidad.servicios.requests.dto.RequestDtos.RequestHistoryResponse;
import pe.edu.universidad.servicios.requests.dto.RequestDtos.RequestSummaryResponse;
import pe.edu.universidad.servicios.requests.repository.RequestActivityRepository;
import pe.edu.universidad.servicios.requests.repository.RequestActivityRepository.Person;
import pe.edu.universidad.servicios.requests.repository.RequestRow;
import pe.edu.universidad.servicios.requests.repository.ServiceRequestRepository;
import pe.edu.universidad.servicios.requests.repository.ServiceRequestRepository.ListFilter;
import pe.edu.universidad.servicios.shared.dto.PageQuery;
import pe.edu.universidad.servicios.shared.dto.PageResponse;
import pe.edu.universidad.servicios.shared.error.ApiException;
import pe.edu.universidad.servicios.shared.security.AuthenticatedActor;
import pe.edu.universidad.servicios.shared.security.RoleCode;
import pe.edu.universidad.servicios.shared.web.Ids;
import pe.edu.universidad.servicios.shared.web.Times;

/**
 * Bandejas, detalle, historial, comentarios (lectura) y auditoría de solicitudes.
 */
@Service
@Transactional(readOnly = true)
public class RequestQueryService {

    private final ServiceRequestRepository requests;
    private final RequestActivityRepository activity;
    private final RequestAccessService access;
    private final RequestStatusCatalogService statuses;
    private final AuditQueryService auditQuery;
    private final Clock clock;

    public RequestQueryService(ServiceRequestRepository requests, RequestActivityRepository activity,
            RequestAccessService access, RequestStatusCatalogService statuses, AuditQueryService auditQuery, Clock clock) {
        this.requests = requests;
        this.activity = activity;
        this.access = access;
        this.statuses = statuses;
        this.auditQuery = auditQuery;
        this.clock = clock;
    }

    public PageResponse<RequestSummaryResponse> list(AuthenticatedActor actor, String estado, String categoriaId,
            String prioridadId, PageQuery page) {
        RequestAccessService.requireParticipantRole(actor);
        ListFilter filter = switch (actor.role()) {
            case ESTUDIANTE, DOCENTE -> new ListFilter(actor.userId(), null, null, null, null, null, false);
            case TECNICO -> new ListFilter(null, actor.userId(), null, null, null, null, true);
            case SUPERVISOR -> {
                if (actor.areaId() == null) {
                    throw ApiException.forbidden("SIN_AREA", "El supervisor no tiene un área asignada.");
                }
                String estadoFiltro = estado == null || estado.isBlank() ? null : estado.trim();
                if (estadoFiltro != null && statuses.findByCode(estadoFiltro).isEmpty()) {
                    throw ApiException.invalidField("estado", "El estado no es válido.");
                }
                yield new ListFilter(null, null, actor.areaId(), estadoFiltro, Ids.parse("categoriaId", categoriaId),
                        Ids.parse("prioridadId", prioridadId), false);
            }
            case ADMINISTRADOR -> throw ApiException.forbidden();
        };
        return new PageResponse<>(requests.list(filter, page).stream().map(RequestMapper::summary).toList(),
                page.page(), page.size(), requests.count(filter));
    }

    public RequestDetailResponse detail(AuthenticatedActor actor, long id) {
        RequestRow row = access.requireVisible(actor, id);
        Person ultimo = row.esFinal() ? activity.lastTechnician(id).orElse(null) : null;
        return RequestMapper.detail(row, ultimo, activity.surveyExists(id), clock.instant());
    }

    public PageResponse<RequestHistoryResponse> history(AuthenticatedActor actor, long id, PageQuery page) {
        access.requireVisible(actor, id);
        return new PageResponse<>(activity.history(id, page).stream()
                .map(h -> new RequestHistoryResponse(h.estadoAnterior(), h.estado(), Ids.str(h.actorId()), h.actorNombre(),
                        Times.iso(h.fecha()), h.motivo()))
                .toList(), page.page(), page.size(), activity.countHistory(id));
    }

    public PageResponse<CommentResponse> comments(AuthenticatedActor actor, long id, PageQuery page) {
        access.requireVisible(actor, id);
        return new PageResponse<>(activity.comments(id, page).stream()
                .map(c -> new CommentResponse(Ids.str(c.id()), c.contenido(),
                        new PersonResponse(Ids.str(c.autorId()), c.autorNombre()), Times.iso(c.creadoEn())))
                .toList(), page.page(), page.size(), activity.countComments(id));
    }

    /** HU-19: solo el supervisor del área de la solicitud consulta su auditoría. */
    public PageResponse<RequestAuditEventResponse> auditEvents(AuthenticatedActor actor, long id, PageQuery page) {
        actor.requireRole(RoleCode.SUPERVISOR);
        access.requireVisible(actor, id);
        return auditQuery.forRequest(id, page).map(e -> new RequestAuditEventResponse(Ids.str(e.id()), e.accion(),
                e.campo(), e.valorAnterior(), e.valorNuevo(), Ids.str(e.actorId()), Times.iso(e.ocurridoEn())));
    }
}
