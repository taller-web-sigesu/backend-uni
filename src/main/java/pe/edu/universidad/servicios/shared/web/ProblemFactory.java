package pe.edu.universidad.servicios.shared.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import pe.edu.universidad.servicios.shared.dto.FieldErrorResponse;

public final class ProblemFactory {

    private ProblemFactory() {
    }

    public static ProblemDetail create(HttpStatus status, String codigo, String detail, List<FieldErrorResponse> errores) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title(status));
        problem.setProperty("codigo", codigo);
        problem.setProperty("correlationId", CorrelationIdFilter.current());
        if (errores != null && !errores.isEmpty()) {
            problem.setProperty("errores", errores);
        }
        return problem;
    }

    public static String title(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST -> "Solicitud inválida";
            case UNAUTHORIZED -> "No autenticado";
            case FORBIDDEN -> "Acceso denegado";
            case NOT_FOUND -> "No encontrado";
            case CONFLICT -> "Conflicto";
            case TOO_MANY_REQUESTS -> "Demasiadas solicitudes";
            case METHOD_NOT_ALLOWED -> "Método no permitido";
            case UNSUPPORTED_MEDIA_TYPE -> "Tipo de contenido no soportado";
            default -> status.is5xxServerError() ? "Error interno" : status.getReasonPhrase();
        };
    }
}
