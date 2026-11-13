package pe.edu.universidad.servicios.shared.web;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestCookieException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import pe.edu.universidad.servicios.shared.dto.FieldErrorResponse;
import pe.edu.universidad.servicios.shared.error.ApiException;

/**
 * Traduce errores a application/problem+json con codigo y correlationId.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ProblemDetail> handleApi(ApiException ex) {
        return respond(ex.getStatus(), ex.getCodigo(), ex.getMessage(), ex.getErrores());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex) {
        List<FieldErrorResponse> errores = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> new FieldErrorResponse(e.getField(), e.getDefaultMessage()))
                .toList();
        return respond(HttpStatus.BAD_REQUEST, "DATOS_INVALIDOS", "Los datos enviados no son válidos.", errores);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ProblemDetail> handleMethodValidation(HandlerMethodValidationException ex) {
        List<FieldErrorResponse> errores = new ArrayList<>();
        for (ParameterValidationResult result : ex.getParameterValidationResults()) {
            if (result instanceof ParameterErrors parameterErrors) {
                parameterErrors.getFieldErrors()
                        .forEach(e -> errores.add(new FieldErrorResponse(e.getField(), e.getDefaultMessage())));
            } else {
                String name = result.getMethodParameter().getParameterName();
                result.getResolvableErrors().forEach(e -> errores.add(new FieldErrorResponse(name, e.getDefaultMessage())));
            }
        }
        return respond(HttpStatus.BAD_REQUEST, "DATOS_INVALIDOS", "Los datos enviados no son válidos.", errores);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ProblemDetail> handleUnreadable(HttpMessageNotReadableException ex) {
        return respond(HttpStatus.BAD_REQUEST, "CUERPO_INVALIDO", "El cuerpo de la petición no es válido.", List.of());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ProblemDetail> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String field = ex.getName();
        return respond(HttpStatus.BAD_REQUEST, "DATOS_INVALIDOS", "El parámetro " + field + " no es válido.",
                List.of(new FieldErrorResponse(field, "Valor con formato inválido.")));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ProblemDetail> handleMissingParam(MissingServletRequestParameterException ex) {
        String field = ex.getParameterName();
        return respond(HttpStatus.BAD_REQUEST, "DATOS_INVALIDOS", "Falta el parámetro " + field + ".",
                List.of(new FieldErrorResponse(field, "Es obligatorio.")));
    }

    /** Archivo por encima del límite de multipart (6 MB): mismo mensaje que el límite de 5 MB del servicio. */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ProblemDetail> handleUploadSize(MaxUploadSizeExceededException ex) {
        return respond(HttpStatus.BAD_REQUEST, "DATOS_INVALIDOS", "El archivo no es válido.",
                List.of(new FieldErrorResponse("archivo", "El tamaño máximo es 5 MB.")));
    }

    @ExceptionHandler({ MissingServletRequestPartException.class, MultipartException.class })
    ResponseEntity<ProblemDetail> handleMultipart(Exception ex) {
        return respond(HttpStatus.BAD_REQUEST, "DATOS_INVALIDOS", "El archivo no es válido.",
                List.of(new FieldErrorResponse("archivo", "Adjunte un archivo en el campo archivo.")));
    }

    @ExceptionHandler(MissingRequestCookieException.class)
    ResponseEntity<ProblemDetail> handleMissingCookie(MissingRequestCookieException ex) {
        return respond(HttpStatus.UNAUTHORIZED, "SESION_INVALIDA", "Se requiere una credencial de sesión válida.", List.of());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ProblemDetail> handleMethod(HttpRequestMethodNotSupportedException ex) {
        return respond(HttpStatus.METHOD_NOT_ALLOWED, "METODO_NO_PERMITIDO", "El método HTTP no está permitido.", List.of());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ProblemDetail> handleMediaType(HttpMediaTypeNotSupportedException ex) {
        return respond(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "TIPO_NO_SOPORTADO", "Use Content-Type application/json.", List.of());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ProblemDetail> handleNoResource(NoResourceFoundException ex) {
        return respond(HttpStatus.NOT_FOUND, "RECURSO_NO_ENCONTRADO", "La ruta solicitada no existe.", List.of());
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex) {
        return respond(HttpStatus.FORBIDDEN, "ACCESO_DENEGADO", "La operación no está permitida para el rol actual.", List.of());
    }

    @ExceptionHandler({ DuplicateKeyException.class, DataIntegrityViolationException.class })
    ResponseEntity<ProblemDetail> handleIntegrity(DataIntegrityViolationException ex) {
        log.info("Conflicto de integridad de datos: {}", ex.getMostSpecificCause().getClass().getSimpleName());
        return respond(HttpStatus.CONFLICT, "CONFLICTO_DATOS", "La operación entra en conflicto con datos existentes.", List.of());
    }

    @ExceptionHandler(PessimisticLockingFailureException.class)
    ResponseEntity<ProblemDetail> handleLock(PessimisticLockingFailureException ex) {
        return respond(HttpStatus.CONFLICT, "CONFLICTO_CONCURRENCIA", "El recurso fue modificado por otra operación. Vuelva a consultarlo.", List.of());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
        log.error("Error no controlado", ex);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "ERROR_INTERNO", "Ocurrió un error inesperado.", List.of());
    }

    private ResponseEntity<ProblemDetail> respond(HttpStatus status, String codigo, String detail, List<FieldErrorResponse> errores) {
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(ProblemFactory.create(status, codigo, detail, errores));
    }
}
