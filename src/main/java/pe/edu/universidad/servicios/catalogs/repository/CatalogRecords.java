package pe.edu.universidad.servicios.catalogs.repository;

/**
 * Filas de catálogos expuestas a otros módulos mediante los servicios de {@code catalogs}.
 */
public final class CatalogRecords {

    private CatalogRecords() {
    }

    public record Area(long id, String codigo, String nombre, String descripcion, boolean activo) {
    }

    public record Category(long id, String codigo, String nombre, String descripcion, long areaId,
            long prioridadPredeterminadaId, boolean activo) {
    }

    public record Priority(long id, String codigo, String nombre, int nivel, String color, boolean activo) {
    }

    public record RequestStatus(long id, String codigo, String nombre, boolean esFinal, int orden) {
    }

    public record Transition(long id, String origen, String destino, String rol, boolean requiereMotivo) {
    }
}
