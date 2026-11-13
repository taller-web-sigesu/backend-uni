package pe.edu.universidad.servicios.requests.service;

import java.time.Clock;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.universidad.servicios.audit.service.AuditEventService;
import pe.edu.universidad.servicios.requests.dto.RequestDtos.SubmitSurveyRequest;
import pe.edu.universidad.servicios.requests.dto.RequestDtos.SurveyResponse;
import pe.edu.universidad.servicios.requests.repository.RequestActivityRepository;
import pe.edu.universidad.servicios.requests.repository.RequestRow;
import pe.edu.universidad.servicios.shared.error.ApiException;
import pe.edu.universidad.servicios.shared.security.AuthenticatedActor;
import pe.edu.universidad.servicios.shared.security.RoleCode;
import pe.edu.universidad.servicios.shared.web.Ids;
import pe.edu.universidad.servicios.shared.web.Patch;

/**
 * Encuesta de satisfacción (HU-20): una por solicitud cerrada, solo del propietario.
 * La unicidad en base respalda el 409 ante envíos concurrentes.
 */
@Service
public class RequestSurveyService {

    private final RequestAccessService access;
    private final RequestActivityRepository activity;
    private final AuditEventService audit;
    private final Clock clock;

    public RequestSurveyService(RequestAccessService access, RequestActivityRepository activity, AuditEventService audit,
            Clock clock) {
        this.access = access;
        this.activity = activity;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public SurveyResponse submit(AuthenticatedActor actor, long requestId, SubmitSurveyRequest body) {
        actor.requireRole(RoleCode.ESTUDIANTE, RoleCode.DOCENTE);
        RequestRow request = access.requireVisible(actor, requestId);
        if (!"CERRADO".equals(request.estadoCodigo())) {
            throw ApiException.conflict("SOLICITUD_NO_CERRADA", "La encuesta solo se registra para solicitudes cerradas.");
        }
        if (activity.surveyExists(requestId)) {
            throw duplicated();
        }
        String comentario = Patch.trimToNull(body.comentario());
        try {
            long id = activity.insertSurvey(requestId, actor.userId(), body.calificacion(), comentario, clock.instant());
            audit.record("encuestas_satisfaccion", id, requestId, "CREAR", "calificacion", null,
                    String.valueOf(body.calificacion()), actor.userId());
            return new SurveyResponse(Ids.str(id), Ids.str(requestId), body.calificacion(), comentario);
        } catch (DuplicateKeyException ex) {
            throw duplicated();
        }
    }

    private static ApiException duplicated() {
        return ApiException.conflict("ENCUESTA_REGISTRADA", "La solicitud ya tiene una encuesta registrada.");
    }
}
