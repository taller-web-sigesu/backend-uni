package pe.edu.universidad.servicios.attachments.repository;

import java.time.Instant;

public record AttachmentRow(
        long id,
        long solicitudId,
        long autorId,
        String autorNombre,
        String nombreOriginal,
        String proveedor,
        String claveObjeto,
        String tipoMime,
        long tamanoBytes,
        Instant creadoEn) {
}
