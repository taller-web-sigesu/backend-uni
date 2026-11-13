package pe.edu.universidad.servicios.catalogs.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Esquemas HTTP de catálogos del servicio (HU-03, HU-16 y HU-17).
 */
public final class CatalogDtos {

    private CatalogDtos() {
    }

    static final String CODE_PATTERN = "[A-Z0-9_\\-]+";

    // Áreas
    public record CreateAreaRequest(
            @NotBlank(message = "codigo es obligatorio.") @Size(max = 50) @Pattern(regexp = CODE_PATTERN, message = "codigo admite mayúsculas, dígitos, guion y guion bajo.") String codigo,
            @NotBlank(message = "nombre es obligatorio.") @Size(max = 120) String nombre,
            @Size(max = 255) String descripcion) {
    }

    public record UpdateAreaRequest(@Size(min = 1, max = 120) String nombre, @Size(max = 255) String descripcion, Boolean activo,
            String codigo) {
    }

    public record AreaResponse(String id, String codigo, String nombre, String descripcion, boolean activo) {
    }

    // Categorías
    public record CreateCategoryRequest(
            @NotBlank(message = "codigo es obligatorio.") @Size(max = 50) @Pattern(regexp = CODE_PATTERN, message = "codigo admite mayúsculas, dígitos, guion y guion bajo.") String codigo,
            @NotBlank(message = "nombre es obligatorio.") @Size(max = 120) String nombre,
            @NotBlank(message = "areaId es obligatorio.") String areaId,
            @NotBlank(message = "prioridadPredeterminadaId es obligatorio.") String prioridadPredeterminadaId,
            @Size(max = 255) String descripcion) {
    }

    public record UpdateCategoryRequest(@Size(min = 1, max = 120) String nombre, @Size(max = 255) String descripcion,
            String areaId, String prioridadPredeterminadaId, Boolean activo, String codigo) {
    }

    public record CategoryResponse(String id, String codigo, String nombre, String descripcion, String areaId,
            String prioridadPredeterminadaId, boolean activo) {
    }

    public record AvailableCategoryResponse(String id, String nombre, String areaId) {
    }

    // Prioridades
    public record CreatePriorityRequest(
            @NotBlank(message = "codigo es obligatorio.") @Size(max = 30) @Pattern(regexp = CODE_PATTERN, message = "codigo admite mayúsculas, dígitos, guion y guion bajo.") String codigo,
            @NotBlank(message = "nombre es obligatorio.") @Size(max = 60) String nombre,
            @NotNull(message = "nivel es obligatorio.") @Min(value = 1, message = "nivel debe estar entre 1 y 10.") @Max(value = 10, message = "nivel debe estar entre 1 y 10.") Integer nivel,
            @Pattern(regexp = "#[0-9A-Fa-f]{6}", message = "color debe tener el formato #RRGGBB.") String color) {
    }

    public record UpdatePriorityRequest(@Size(min = 1, max = 60) String nombre,
            @Min(value = 1, message = "nivel debe estar entre 1 y 10.") @Max(value = 10, message = "nivel debe estar entre 1 y 10.") Integer nivel,
            @Pattern(regexp = "#[0-9A-Fa-f]{6}", message = "color debe tener el formato #RRGGBB.") String color,
            Boolean activo, String codigo) {
    }

    public record PriorityResponse(String id, String codigo, String nombre, int nivel, String color, boolean activo) {
    }

    // Estados de solicitud
    public record RenameRequestStatusRequest(@Size(max = 60) String nombre, String codigo) {
    }

    public record RequestStatusResponse(String id, String codigo, String nombre, boolean esFinal) {
    }
}
