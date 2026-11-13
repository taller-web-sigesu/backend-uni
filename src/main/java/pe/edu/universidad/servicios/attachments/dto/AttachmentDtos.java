package pe.edu.universidad.servicios.attachments.dto;

/**
 * Esquemas HTTP de evidencias (HU-26). Nunca exponen la clave del objeto ni el proveedor.
 */
public final class AttachmentDtos {

    private AttachmentDtos() {
    }

    public record AttachmentResponse(String id, String nombre, long tamanoBytes, String tipoMime, String autorId,
            String autorNombre, String creadoEn, boolean puedeEliminar) {
    }
}
