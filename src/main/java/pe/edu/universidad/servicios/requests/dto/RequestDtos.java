package pe.edu.universidad.servicios.requests.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Esquemas HTTP de registro, seguimiento, auditoría y satisfacción de solicitudes.
 */
public final class RequestDtos {

    private RequestDtos() {
    }

    public record CreateRequestRequest(
            @NotBlank(message = "categoriaId es obligatorio.") String categoriaId,
            @NotBlank(message = "ubicacion es obligatoria.") @Size(max = 255, message = "ubicacion admite hasta 255 caracteres.") String ubicacion,
            @NotBlank(message = "descripcion es obligatoria.") @Size(max = 5000, message = "descripcion admite hasta 5000 caracteres.") String descripcion) {
    }

    public record PersonResponse(String id, String nombreCompleto) {
    }

    public record RequestSummaryResponse(String id, String codigo, String categoriaId, String areaId, String prioridadId,
            String estado, String creadoEn, PersonResponse responsableActual) {
    }

    public record SlaResponse(String estado, String fechaAviso, String fechaLimite) {
    }

    public record RequestDetailResponse(String id, String codigo, String categoriaId, String categoria, String areaId,
            String prioridadId, String prioridad, String estado, String estadoNombre, String creadoEn,
            PersonResponse solicitante, String ubicacion, String descripcion, String motivoRechazo,
            PersonResponse responsableActual, PersonResponse ultimoResponsable, SlaResponse sla,
            String resueltaEn, String cerradaEn) {
    }

    public record RequestHistoryResponse(String estadoAnterior, String estado, String actorId, String actorNombre,
            String fecha, String motivo) {
    }

    public record CreateCommentRequest(
            @NotBlank(message = "contenido es obligatorio.") @Size(max = 5000, message = "contenido admite hasta 5000 caracteres.") String contenido) {
    }

    public record CommentResponse(String id, String contenido, PersonResponse autor, String creadoEn) {
    }

    public record SubmitSurveyRequest(
            @NotNull(message = "calificacion es obligatoria.") @Min(value = 1, message = "calificacion debe estar entre 1 y 5.") @Max(value = 5, message = "calificacion debe estar entre 1 y 5.") Integer calificacion,
            @Size(max = 2000, message = "comentario admite hasta 2000 caracteres.") String comentario) {
    }

    public record SurveyResponse(String id, String solicitudId, int calificacion, String comentario) {
    }

    public record RequestAuditEventResponse(String id, String accion, String campo, String valorAnterior,
            String valorNuevo, String actorId, String fecha) {
    }
}
