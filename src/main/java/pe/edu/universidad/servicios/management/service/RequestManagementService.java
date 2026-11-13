package pe.edu.universidad.servicios.management.service;

import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.universidad.servicios.management.dto.ManagementDtos.AssignTechnicianRequest;
import pe.edu.universidad.servicios.management.dto.ManagementDtos.AssignmentResponse;
import pe.edu.universidad.servicios.management.dto.ManagementDtos.ChangePriorityRequest;
import pe.edu.universidad.servicios.management.dto.ManagementDtos.ChangeRequestStatusRequest;
import pe.edu.universidad.servicios.requests.dto.RequestDtos.RequestSummaryResponse;
import pe.edu.universidad.servicios.requests.repository.RequestRow;
import pe.edu.universidad.servicios.requests.service.RequestAccessService;
import pe.edu.universidad.servicios.requests.service.RequestMapper;
import pe.edu.universidad.servicios.requests.service.RequestWorkflowService;
import pe.edu.universidad.servicios.requests.service.RequestWorkflowService.AssignmentResult;
import pe.edu.universidad.servicios.shared.dto.PageQuery;
import pe.edu.universidad.servicios.shared.dto.PageResponse;
import pe.edu.universidad.servicios.shared.error.ApiException;
import pe.edu.universidad.servicios.shared.security.AuthenticatedActor;
import pe.edu.universidad.servicios.shared.security.RoleCode;
import pe.edu.universidad.servicios.shared.web.Ids;
import pe.edu.universidad.servicios.shared.web.Times;
import pe.edu.universidad.servicios.users.dto.UserDtos.EligibleTechnicianResponse;
import pe.edu.universidad.servicios.users.repository.UserRecord;
import pe.edu.universidad.servicios.users.service.TechnicianDirectoryService;

/**
 * Operaciones del supervisor y del técnico (HU-07 a HU-11). Valida rol y alcance y delega
 * las escrituras en {@link RequestWorkflowService}.
 */
@Service
public class RequestManagementService {

    private final RequestAccessService access;
    private final RequestWorkflowService workflow;
    private final TechnicianDirectoryService technicians;

    public RequestManagementService(RequestAccessService access, RequestWorkflowService workflow,
            TechnicianDirectoryService technicians) {
        this.access = access;
        this.workflow = workflow;
        this.technicians = technicians;
    }

    @Transactional(readOnly = true)
    public PageResponse<EligibleTechnicianResponse> eligibleTechnicians(AuthenticatedActor actor, long requestId, PageQuery page) {
        actor.requireRole(RoleCode.SUPERVISOR);
        RequestRow request = access.requireVisible(actor, requestId);
        return technicians.eligible(request.areaId(), page);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AssignmentResponse assign(AuthenticatedActor actor, long requestId, AssignTechnicianRequest body) {
        actor.requireRole(RoleCode.SUPERVISOR);
        long technicianId = Ids.required("tecnicoId", body.tecnicoId());
        Long priorityId = Ids.parse("prioridadId", body.prioridadId());
        RequestRow current = lockInArea(actor, requestId);
        if (current.esFinal()) {
            throw ApiException.conflict("SOLICITUD_TERMINAL", "La solicitud está en un estado final y no admite cambios.");
        }
        UserRecord technician = technicians.findEligible(technicianId, current.areaId())
                .orElseThrow(() -> ApiException.invalidField("tecnicoId", "El técnico no está activo o no pertenece al área de la solicitud."));
        AssignmentResult result = workflow.assign(current, technician, priorityId, actor.userId());
        RequestRow updated = result.request();
        return new AssignmentResponse(Ids.str(result.assignmentId()), Ids.str(updated.id()), Ids.str(technician.id()),
                Times.iso(result.assignedAt()), updated.estadoCodigo(), Ids.str(updated.prioridadId()));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public RequestSummaryResponse changePriority(AuthenticatedActor actor, long requestId, ChangePriorityRequest body) {
        actor.requireRole(RoleCode.SUPERVISOR);
        long priorityId = Ids.required("prioridadId", body.prioridadId());
        RequestRow current = lockInArea(actor, requestId);
        return RequestMapper.summary(workflow.changePriority(current, priorityId, actor.userId()));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public RequestSummaryResponse changeStatus(AuthenticatedActor actor, long requestId, ChangeRequestStatusRequest body) {
        actor.requireRole(RoleCode.TECNICO, RoleCode.SUPERVISOR);
        RequestRow current = workflow.lock(requestId);
        boolean inScope = actor.role() == RoleCode.TECNICO
                ? Objects.equals(current.responsableId(), actor.userId())
                : actor.isSupervisorOf(current.areaId());
        if (!inScope) {
            throw RequestAccessService.notFound();
        }
        RequestRow updated = workflow.changeStatus(current, body.estado().trim(), body.motivo(), actor.userId(), actor.role());
        return RequestMapper.summary(updated);
    }

    private RequestRow lockInArea(AuthenticatedActor actor, long requestId) {
        RequestRow current = workflow.lock(requestId);
        if (!actor.isSupervisorOf(current.areaId())) {
            throw RequestAccessService.notFound();
        }
        return current;
    }
}
