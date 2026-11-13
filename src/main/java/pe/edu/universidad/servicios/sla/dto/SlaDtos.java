package pe.edu.universidad.servicios.sla.dto;

import java.time.Instant;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public final class SlaDtos {

    private SlaDtos() {
    }

    public record CreateSlaPolicyRequest(
            String categoriaId,
            String prioridadId,
            @NotNull(message = "horasObjetivo es obligatorio.") @Min(value = 1, message = "horasObjetivo debe ser positivo.") @Max(value = 65535, message = "horasObjetivo es demasiado grande.") Integer horasObjetivo,
            @NotNull(message = "horasAviso es obligatorio.") @Min(value = 0, message = "horasAviso no puede ser negativo.") @Max(value = 65535, message = "horasAviso es demasiado grande.") Integer horasAviso,
            Instant vigenteDesde,
            Instant vigenteHasta) {
    }

    public record UpdateSlaPolicyRequest(
            @Min(value = 1, message = "horasObjetivo debe ser positivo.") @Max(value = 65535, message = "horasObjetivo es demasiado grande.") Integer horasObjetivo,
            @Min(value = 0, message = "horasAviso no puede ser negativo.") @Max(value = 65535, message = "horasAviso es demasiado grande.") Integer horasAviso,
            Instant vigenteDesde,
            Instant vigenteHasta,
            Boolean activo,
            String categoriaId,
            String prioridadId) {
    }

    public record SlaPolicyResponse(String id, String categoriaId, String prioridadId, int horasObjetivo, int horasAviso,
            String vigenteDesde, String vigenteHasta, boolean activo) {
    }
}
