package pe.edu.universidad.servicios.requests.repository;

import java.time.Instant;

/**
 * Proyección de una solicitud con los códigos de catálogo necesarios para reglas y respuestas.
 */
public record RequestRow(
        long id,
        String codigo,
        long solicitanteId,
        String solicitanteNombre,
        long categoriaId,
        String categoriaNombre,
        long areaId,
        String areaNombre,
        long prioridadId,
        String prioridadCodigo,
        String prioridadNombre,
        String estadoCodigo,
        String estadoNombre,
        boolean esFinal,
        Long responsableId,
        String responsableNombre,
        String ubicacion,
        String descripcion,
        String motivoRechazo,
        Instant fechaLimiteSla,
        Instant fechaAvisoSla,
        Instant resueltaEn,
        Instant cerradaEn,
        int version,
        Instant creadoEn) {
}
