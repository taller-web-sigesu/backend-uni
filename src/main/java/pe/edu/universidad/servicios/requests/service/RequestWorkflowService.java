package pe.edu.universidad.servicios.requests.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.universidad.servicios.audit.service.AuditEventService;
import pe.edu.universidad.servicios.catalogs.repository.CatalogRecords.Priority;
import pe.edu.universidad.servicios.catalogs.service.PriorityCatalogService;
import pe.edu.universidad.servicios.catalogs.service.RequestStatusCatalogService;
import pe.edu.universidad.servicios.management.domain.RequestTransitionPolicy;
import pe.edu.universidad.servicios.requests.repository.RequestActivityRepository;
import pe.edu.universidad.servicios.requests.repository.RequestRow;
import pe.edu.universidad.servicios.requests.repository.ServiceRequestRepository;
import pe.edu.universidad.servicios.shared.error.ApiException;
import pe.edu.universidad.servicios.shared.security.RoleCode;
import pe.edu.universidad.servicios.users.repository.UserRecord;

/**
 * Único punto de escritura de prioridad, asignación y estado de una solicitud. Solicitud, asignación,
 * historial y auditoría se guardan en una transacción con la fila bloqueada y control de versión.
 */
@Service
public class RequestWorkflowService {

    private final ServiceRequestRepository requests;
    private final RequestActivityRepository activity;
    private final RequestStatusCatalogService statuses;
    private final PriorityCatalogService priorities;
    private final AuditEventService audit;
    private final Clock clock;

    public RequestWorkflowService(ServiceRequestRepository requests, RequestActivityRepository activity,
            RequestStatusCatalogService statuses, PriorityCatalogService priorities, AuditEventService audit, Clock clock) {
        this.requests = requests;
        this.activity = activity;
        this.statuses = statuses;
        this.priorities = priorities;
        this.audit = audit;
        this.clock = clock;
    }

    public record AssignmentResult(long assignmentId, RequestRow request, Instant assignedAt) {
    }

    /** Bloquea la solicitud; el llamador valida el alcance sobre la fila devuelta. */
    @Transactional(propagation = Propagation.MANDATORY)
    public RequestRow lock(long requestId) {
        return requests.lockById(requestId).orElseThrow(RequestAccessService::notFound);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public AssignmentResult assign(RequestRow current, UserRecord technician, Long newPriorityId, long actorId) {
        ensureOpen(current);
        if (Objects.equals(current.responsableId(), technician.id())) {
            throw ApiException.conflict("YA_ASIGNADO", "La solicitud ya está asignada a ese técnico.");
        }
        Instant now = clock.instant();
        Priority priority = resolvePriority(current, newPriorityId);
        boolean firstAssignment = "NUEVO".equals(current.estadoCodigo());
        String targetState = firstAssignment ? "ASIGNADO" : current.estadoCodigo();
        long targetStateId = statuses.requireByCode(targetState).id();

        activity.finishActiveAssignment(current.id(), "REASIGNACION", now);
        long assignmentId = activity.insertAssignment(current.id(), technician.id(), actorId, now);
        persist(current, priority.id(), targetStateId, technician.id(), current.motivoRechazo(), current.resueltaEn(),
                current.cerradaEn(), now);

        audit.record("solicitudes", current.id(), current.id(), firstAssignment ? "ASIGNAR" : "REASIGNAR", "responsable",
                current.responsableNombre(), technician.nombreCompleto(), actorId);
        if (priority.id() != current.prioridadId()) {
            audit.record("solicitudes", current.id(), current.id(), "ACTUALIZAR", "prioridad", current.prioridadCodigo(),
                    priority.codigo(), actorId);
        }
        if (firstAssignment) {
            activity.insertHistory(current.id(), statuses.requireByCode("NUEVO").id(), targetStateId, actorId, null, now);
            audit.record("solicitudes", current.id(), current.id(), "CAMBIAR_ESTADO", "estado", "NUEVO", targetState, actorId);
        }
        return new AssignmentResult(assignmentId, requests.findById(current.id()).orElseThrow(), now);
    }

    /** Cambia la prioridad de una solicitud abierta; el SLA congelado no se recalcula. */
    @Transactional(propagation = Propagation.MANDATORY)
    public RequestRow changePriority(RequestRow current, long priorityId, long actorId) {
        ensureOpen(current);
        Priority priority = resolvePriority(current, priorityId);
        if (priority.id() == current.prioridadId()) {
            return current;
        }
        Instant now = clock.instant();
        persist(current, priority.id(), statuses.requireByCode(current.estadoCodigo()).id(), current.responsableId(),
                current.motivoRechazo(), current.resueltaEn(), current.cerradaEn(), now);
        audit.record("solicitudes", current.id(), current.id(), "ACTUALIZAR", "prioridad", current.prioridadCodigo(),
                priority.codigo(), actorId);
        return requests.findById(current.id()).orElseThrow();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public RequestRow changeStatus(RequestRow current, String target, String motivo, long actorId,
            RoleCode role) {
        if (statuses.findByCode(target).isEmpty()) {
            throw ApiException.invalidField("estado", "El estado no es válido.");
        }
        RequestTransitionPolicy.Decision decision = RequestTransitionPolicy.evaluate(statuses.activeTransitions(), role,
                current.estadoCodigo(), current.esFinal(), target, motivo);
        Instant now = clock.instant();
        String reason = decision.requiresReason() ? motivo.trim() : null;
        Long responsable = current.responsableId();
        Instant resuelta = current.resueltaEn();
        Instant cerrada = current.cerradaEn();
        String motivoRechazo = current.motivoRechazo();
        switch (target) {
            case "RESUELTO" -> resuelta = now;
            case "CERRADO" -> cerrada = now;
            case "RECHAZADO" -> {
                cerrada = now;
                motivoRechazo = reason;
            }
            default -> {
            }
        }
        long targetId = statuses.requireByCode(target).id();
        boolean terminal = statuses.requireByCode(target).esFinal();
        if (terminal) {
            // Cerrar o rechazar termina la asignación vigente; el último responsable queda en asignaciones.
            activity.finishActiveAssignment(current.id(), target, now);
            responsable = null;
        }
        persist(current, current.prioridadId(), targetId, responsable, motivoRechazo, resuelta, cerrada, now);
        activity.insertHistory(current.id(), statuses.requireByCode(current.estadoCodigo()).id(), targetId, actorId, reason, now);
        audit.record("solicitudes", current.id(), current.id(), "CAMBIAR_ESTADO", "estado", current.estadoCodigo(), target, actorId);
        return requests.findById(current.id()).orElseThrow();
    }

    private void persist(RequestRow current, long priorityId, long stateId, Long responsable, String motivoRechazo,
            Instant resuelta, Instant cerrada, Instant now) {
        boolean updated = requests.update(current.id(), current.version(), priorityId, stateId, responsable, motivoRechazo,
                resuelta, cerrada, now);
        if (!updated) {
            throw ApiException.conflict("CONFLICTO_CONCURRENCIA", "La solicitud fue modificada por otra operación. Vuelva a consultarla.");
        }
    }

    private Priority resolvePriority(RequestRow current, Long priorityId) {
        if (priorityId == null || priorityId == current.prioridadId()) {
            return priorities.find(current.prioridadId()).orElseThrow();
        }
        return priorities.findActive(priorityId)
                .orElseThrow(() -> ApiException.invalidField("prioridadId", "La prioridad no existe o no está activa."));
    }

    private static void ensureOpen(RequestRow current) {
        if (current.esFinal()) {
            throw ApiException.conflict("SOLICITUD_TERMINAL", "La solicitud está en un estado final y no admite cambios.");
        }
    }
}
