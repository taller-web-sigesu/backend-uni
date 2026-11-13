package pe.edu.universidad.servicios.management.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class ManagementDtos {

    private ManagementDtos() {
    }

    public record AssignTechnicianRequest(@NotBlank(message = "tecnicoId es obligatorio.") String tecnicoId, String prioridadId) {
    }

    public record AssignmentResponse(String id, String solicitudId, String tecnicoId, String asignadaEn,
            String estadoSolicitud, String prioridadId) {
    }

    public record ChangePriorityRequest(@NotBlank(message = "prioridadId es obligatorio.") String prioridadId) {
    }

    public record ChangeRequestStatusRequest(
            @NotBlank(message = "estado es obligatorio.") String estado,
            @Size(max = 2000, message = "motivo admite hasta 2000 caracteres.") String motivo) {
    }
}
