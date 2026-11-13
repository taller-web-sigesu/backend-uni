package pe.edu.universidad.servicios.requests.controller;

import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import pe.edu.universidad.servicios.requests.dto.RequestDtos.CommentResponse;
import pe.edu.universidad.servicios.requests.dto.RequestDtos.CreateCommentRequest;
import pe.edu.universidad.servicios.requests.dto.RequestDtos.CreateRequestRequest;
import pe.edu.universidad.servicios.requests.dto.RequestDtos.RequestAuditEventResponse;
import pe.edu.universidad.servicios.requests.dto.RequestDtos.RequestDetailResponse;
import pe.edu.universidad.servicios.requests.dto.RequestDtos.RequestHistoryResponse;
import pe.edu.universidad.servicios.requests.dto.RequestDtos.RequestSummaryResponse;
import pe.edu.universidad.servicios.requests.dto.RequestDtos.SubmitSurveyRequest;
import pe.edu.universidad.servicios.requests.dto.RequestDtos.SurveyResponse;
import pe.edu.universidad.servicios.requests.service.RequestCommentService;
import pe.edu.universidad.servicios.requests.service.RequestQueryService;
import pe.edu.universidad.servicios.requests.service.RequestRegistrationService;
import pe.edu.universidad.servicios.requests.service.RequestSurveyService;
import pe.edu.universidad.servicios.shared.dto.PageQuery;
import pe.edu.universidad.servicios.shared.dto.PageResponse;
import pe.edu.universidad.servicios.shared.security.AuthenticatedActor;
import pe.edu.universidad.servicios.shared.security.CurrentActor;

/**
 * Registro y seguimiento de solicitudes (HU-03 a HU-06), auditoría (HU-19) y encuesta (HU-20).
 */
@RestController
@RequestMapping("/api/v1/requests")
@Tag(name = "Solicitudes", description = "HU-03 a HU-06, HU-19 y HU-20")
public class RequestController {

    private final RequestRegistrationService registration;
    private final RequestQueryService queries;
    private final RequestCommentService comments;
    private final RequestSurveyService surveys;

    public RequestController(RequestRegistrationService registration, RequestQueryService queries,
            RequestCommentService comments, RequestSurveyService surveys) {
        this.registration = registration;
        this.queries = queries;
        this.comments = comments;
        this.surveys = surveys;
    }

    @PostMapping
    @Operation(summary = "Registra una solicitud de servicio")
    public ResponseEntity<RequestSummaryResponse> create(@CurrentActor AuthenticatedActor actor,
            @Valid @RequestBody CreateRequestRequest body) {
        RequestSummaryResponse created = registration.create(actor, body);
        return ResponseEntity.created(URI.create("/api/v1/requests/" + created.id())).body(created);
    }

    @GetMapping
    @Operation(summary = "Lista solicitudes visibles según el rol")
    public PageResponse<RequestSummaryResponse> list(@CurrentActor AuthenticatedActor actor,
            @RequestParam(required = false) String estado, @RequestParam(required = false) String categoriaId,
            @RequestParam(required = false) String prioridadId, @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return queries.list(actor, estado, categoriaId, prioridadId, PageQuery.of(page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consulta el detalle y el SLA de una solicitud")
    public RequestDetailResponse detail(@CurrentActor AuthenticatedActor actor, @PathVariable long id) {
        return queries.detail(actor, id);
    }

    @GetMapping("/{id}/history")
    @Operation(summary = "Consulta las transiciones de la solicitud")
    public PageResponse<RequestHistoryResponse> history(@CurrentActor AuthenticatedActor actor, @PathVariable long id,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return queries.history(actor, id, PageQuery.of(page, size));
    }

    @GetMapping("/{id}/comments")
    @Operation(summary = "Consulta los comentarios de seguimiento")
    public PageResponse<CommentResponse> listComments(@CurrentActor AuthenticatedActor actor, @PathVariable long id,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return queries.comments(actor, id, PageQuery.of(page, size));
    }

    @PostMapping("/{id}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Agrega un comentario de seguimiento")
    public CommentResponse addComment(@CurrentActor AuthenticatedActor actor, @PathVariable long id,
            @Valid @RequestBody CreateCommentRequest body) {
        return comments.create(actor, id, body);
    }

    @GetMapping("/{id}/audit-events")
    @Operation(summary = "Consulta las modificaciones auditadas")
    public PageResponse<RequestAuditEventResponse> auditEvents(@CurrentActor AuthenticatedActor actor, @PathVariable long id,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return queries.auditEvents(actor, id, PageQuery.of(page, size));
    }

    @PostMapping("/{id}/survey")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registra la satisfacción del solicitante")
    public SurveyResponse survey(@CurrentActor AuthenticatedActor actor, @PathVariable long id,
            @Valid @RequestBody SubmitSurveyRequest body) {
        return surveys.submit(actor, id, body);
    }
}
