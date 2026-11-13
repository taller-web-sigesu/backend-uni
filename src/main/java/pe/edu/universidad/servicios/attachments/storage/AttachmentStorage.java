package pe.edu.universidad.servicios.attachments.storage;

import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;

/**
 * Almacén de los archivos de evidencia. MySQL guarda solo los metadatos (DISENIO-BD 6.25 y ADR-0004, punto 10).
 */
public interface AttachmentStorage {

    /** Valor de {@code adjuntos_solicitud.proveedor_almacenamiento}: {@code LOCAL} o {@code SUPABASE}. */
    String provider();

    void put(String key, byte[] content, String mime);

    void delete(String key);

    /** URL firmada temporal para descargar sin pasar por el backend; vacío si el proveedor no las ofrece. */
    Optional<URI> signedDownloadUrl(String key, Duration ttl);

    /** Contenido para transmitir desde el backend cuando no hay URL firmada. */
    InputStream open(String key);
}
