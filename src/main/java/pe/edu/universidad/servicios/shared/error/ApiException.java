package pe.edu.universidad.servicios.shared.error;

import java.util.List;

import org.springframework.http.HttpStatus;

import pe.edu.universidad.servicios.shared.dto.FieldErrorResponse;

/**
 * Error de negocio traducido a application/problem+json por {@code ApiExceptionHandler}.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String codigo;
    private final List<FieldErrorResponse> errores;

    public ApiException(HttpStatus status, String codigo, String detail) {
        this(status, codigo, detail, List.of());
    }

    public ApiException(HttpStatus status, String codigo, String detail, List<FieldErrorResponse> errores) {
        super(detail);
        this.status = status;
        this.codigo = codigo;
        this.errores = List.copyOf(errores);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCodigo() {
        return codigo;
    }

    public List<FieldErrorResponse> getErrores() {
        return errores;
    }

    public static ApiException badRequest(String codigo, String detail) {
        return new ApiException(HttpStatus.BAD_REQUEST, codigo, detail);
    }

    public static ApiException invalidField(String campo, String mensaje) {
        return new ApiException(HttpStatus.BAD_REQUEST, "DATOS_INVALIDOS", mensaje,
                List.of(new FieldErrorResponse(campo, mensaje)));
    }

    public static ApiException unauthorized(String codigo, String detail) {
        return new ApiException(HttpStatus.UNAUTHORIZED, codigo, detail);
    }

    public static ApiException forbidden(String codigo, String detail) {
        return new ApiException(HttpStatus.FORBIDDEN, codigo, detail);
    }

    public static ApiException forbidden() {
        return forbidden("ACCESO_DENEGADO", "La operación no está permitida para el rol actual.");
    }

    public static ApiException notFound(String detail) {
        return new ApiException(HttpStatus.NOT_FOUND, "RECURSO_NO_ENCONTRADO", detail);
    }

    public static ApiException conflict(String codigo, String detail) {
        return new ApiException(HttpStatus.CONFLICT, codigo, detail);
    }

    public static ApiException tooManyRequests(String detail) {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, "LIMITE_INTENTOS", detail);
    }
}
