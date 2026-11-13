package pe.edu.universidad.servicios.attachments.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import pe.edu.universidad.servicios.attachments.domain.FileTypeDetector;
import pe.edu.universidad.servicios.attachments.domain.FileTypeDetector.DetectedType;
import pe.edu.universidad.servicios.attachments.dto.AttachmentDtos.AttachmentResponse;
import pe.edu.universidad.servicios.attachments.repository.AttachmentRepository;
import pe.edu.universidad.servicios.attachments.repository.AttachmentRow;
import pe.edu.universidad.servicios.attachments.storage.AttachmentStorage;
import pe.edu.universidad.servicios.audit.service.AuditEventService;
import pe.edu.universidad.servicios.requests.repository.RequestRow;
import pe.edu.universidad.servicios.requests.service.RequestAccessService;
import pe.edu.universidad.servicios.shared.error.ApiException;
import pe.edu.universidad.servicios.shared.security.AuthenticatedActor;
import pe.edu.universidad.servicios.shared.web.Ids;
import pe.edu.universidad.servicios.shared.web.Times;

/**
 * Evidencias de una solicitud (HU-26, DISENIO-BD 7.10). Ver y descargar sigue la regla de HU-05; adjuntar y eliminar
 * exigen la solicitud abierta, y eliminar solo lo hace el autor.
 */
@Service
public class AttachmentService {

    public static final long MAX_BYTES = 5L * 1024 * 1024;
    public static final int MAX_ACTIVE = 5;
    public static final Duration DOWNLOAD_TTL = Duration.ofMinutes(5);

    private static final Logger log = LoggerFactory.getLogger(AttachmentService.class);

    private final AttachmentRepository attachments;
    private final AttachmentStorage storage;
    private final RequestAccessService access;
    private final AuditEventService audit;
    private final Clock clock;

    public AttachmentService(AttachmentRepository attachments, AttachmentStorage storage, RequestAccessService access,
            AuditEventService audit, Clock clock) {
        this.attachments = attachments;
        this.storage = storage;
        this.access = access;
        this.audit = audit;
        this.clock = clock;
    }

    /** Descarga: URL firmada del proveedor o contenido para transmitir desde el backend. */
    public record Download(AttachmentRow row, Optional<URI> redirect, InputStream content) {
    }

    @Transactional(readOnly = true)
    public List<AttachmentResponse> list(AuthenticatedActor actor, long requestId) {
        RequestRow request = access.requireVisible(actor, requestId);
        return attachments.listActive(requestId).stream().map(a -> toResponse(actor, request, a)).toList();
    }

    @Transactional
    public AttachmentResponse upload(AuthenticatedActor actor, long requestId, MultipartFile file) {
        RequestAccessService.requireParticipantRole(actor);
        byte[] content = read(file);
        if (content.length == 0) {
            throw ApiException.invalidField("archivo", "El archivo está vacío.");
        }
        if (content.length > MAX_BYTES) {
            throw tooLarge();
        }
        DetectedType type = FileTypeDetector.detect(content)
                .orElseThrow(() -> ApiException.invalidField("archivo", "Solo se aceptan archivos JPG, PNG o PDF."));
        String nombre = displayName(file.getOriginalFilename(), type);

        RequestRow request = access.lockVisible(actor, requestId);
        requireOpen(request);
        if (attachments.countActive(requestId) >= MAX_ACTIVE) {
            throw ApiException.conflict("LIMITE_EVIDENCIAS", "La solicitud ya tiene " + MAX_ACTIVE + " evidencias.");
        }
        String key = "solicitudes/" + requestId + "/" + UUID.randomUUID() + "." + type.extension();
        storage.put(key, content, type.mime());
        try {
            long id = attachments.insert(requestId, actor.userId(), nombre, storage.provider(), key, type.mime(),
                    content.length, sha256(content), clock.instant());
            audit.record("adjuntos_solicitud", id, requestId, "ADJUNTAR", "nombre_original", null, nombre, actor.userId());
            return toResponse(actor, request, attachments.findActive(requestId, id).orElseThrow());
        } catch (RuntimeException ex) {
            deleteQuietly(key);
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public Download download(AuthenticatedActor actor, long requestId, long attachmentId) {
        access.requireVisible(actor, requestId);
        AttachmentRow row = attachments.findActive(requestId, attachmentId).orElseThrow(AttachmentService::notFound);
        Optional<URI> signed = storage.signedDownloadUrl(row.claveObjeto(), DOWNLOAD_TTL);
        return new Download(row, signed, signed.isPresent() ? null : storage.open(row.claveObjeto()));
    }

    @Transactional
    public void delete(AuthenticatedActor actor, long requestId, long attachmentId) {
        RequestRow request = access.lockVisible(actor, requestId);
        AttachmentRow row = attachments.findActive(requestId, attachmentId).orElseThrow(AttachmentService::notFound);
        if (row.autorId() != actor.userId()) {
            throw ApiException.forbidden("SOLO_EL_AUTOR", "Solo quien adjuntó la evidencia puede eliminarla.");
        }
        requireOpen(request);
        attachments.markDeleted(row.id(), actor.userId(), clock.instant());
        audit.record("adjuntos_solicitud", row.id(), requestId, "ELIMINAR_ADJUNTO", "nombre_original", row.nombreOriginal(),
                null, actor.userId());
        // El objeto se borra solo si la baja lógica se confirmó; un fallo deja la fila marcada y queda en el log.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                deleteQuietly(row.claveObjeto());
            }
        });
    }

    private void deleteQuietly(String key) {
        try {
            storage.delete(key);
        } catch (RuntimeException ex) {
            log.error("No se pudo borrar el objeto de evidencia {}: {}", key, ex.getClass().getSimpleName());
        }
    }

    private static void requireOpen(RequestRow request) {
        if (request.esFinal()) {
            throw ApiException.conflict("SOLICITUD_FINALIZADA",
                    "La solicitud está cerrada o rechazada y ya no admite cambios en sus evidencias.");
        }
    }

    private static AttachmentResponse toResponse(AuthenticatedActor actor, RequestRow request, AttachmentRow a) {
        return new AttachmentResponse(Ids.str(a.id()), a.nombreOriginal(), a.tamanoBytes(), a.tipoMime(),
                Ids.str(a.autorId()), a.autorNombre(), Times.iso(a.creadoEn()),
                a.autorId() == actor.userId() && !request.esFinal());
    }

    /**
     * Nombre para mostrar: último segmento de la ruta enviada, sin caracteres de control y hasta 255 caracteres.
     * Nunca se usa para construir la clave del objeto.
     */
    static String displayName(String original, DetectedType type) {
        String name = original == null ? "" : original;
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        name = name.substring(slash + 1).replaceAll("\\p{Cntrl}", "").trim();
        if (name.isEmpty() || name.equals(".") || name.equals("..")) {
            name = "evidencia." + type.extension();
        }
        return name.length() > 255 ? name.substring(0, 255) : name;
    }

    static ApiException tooLarge() {
        return ApiException.invalidField("archivo", "El tamaño máximo es 5 MB.");
    }

    private static ApiException notFound() {
        return ApiException.notFound("La evidencia no existe o no está en su alcance.");
    }

    private static byte[] read(MultipartFile file) {
        if (file == null) {
            throw ApiException.invalidField("archivo", "archivo es obligatorio.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw tooLarge();
        }
        try {
            return file.getBytes();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
