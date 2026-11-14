package pe.edu.universidad.servicios.requests.service;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.universidad.servicios.audit.service.AuditEventService;
import pe.edu.universidad.servicios.requests.dto.RequestDtos.CommentResponse;
import pe.edu.universidad.servicios.requests.dto.RequestDtos.CreateCommentRequest;
import pe.edu.universidad.servicios.requests.dto.RequestDtos.PersonResponse;
import pe.edu.universidad.servicios.requests.repository.RequestActivityRepository;
import pe.edu.universidad.servicios.shared.security.AuthenticatedActor;
import pe.edu.universidad.servicios.shared.web.Ids;
import pe.edu.universidad.servicios.shared.web.Times;

/**
 * Comentarios de seguimiento (HU-06): autor autenticado y fecha del servidor.
 */
@Service
public class RequestCommentService {

    private final RequestAccessService access;
    private final RequestActivityRepository activity;
    private final AuditEventService audit;
    private final Clock clock;

    public RequestCommentService(RequestAccessService access, RequestActivityRepository activity, AuditEventService audit,
            Clock clock) {
        this.access = access;
        this.activity = activity;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public CommentResponse create(AuthenticatedActor actor, long requestId, CreateCommentRequest body) {
        access.requireVisible(actor, requestId);
        Instant now = clock.instant();
        String contenido = body.contenido().trim();
        long id = activity.insertComment(requestId, actor.userId(), contenido, now);
        audit.record("comentarios", id, requestId, "COMENTAR", null, null, null, actor.userId());
        return new CommentResponse(Ids.str(id), contenido, new PersonResponse(Ids.str(actor.userId()), actor.nombreCompleto()),
                Times.iso(now));
    }
}
