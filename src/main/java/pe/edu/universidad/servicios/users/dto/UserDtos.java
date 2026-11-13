package pe.edu.universidad.servicios.users.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Esquemas HTTP de usuarios y roles (HU-15). Las respuestas excluyen contraseñas y hashes.
 */
public final class UserDtos {

    private UserDtos() {
    }

    public record CreateUserRequest(
            @NotBlank(message = "nombreCompleto es obligatorio.") @Size(max = 150) String nombreCompleto,
            @NotBlank(message = "correo es obligatorio.") @Email(message = "correo no tiene un formato válido.") @Size(max = 150) String correo,
            @NotBlank(message = "contrasenaInicial es obligatoria.") String contrasenaInicial,
            @NotBlank(message = "rol es obligatorio.") String rol,
            String areaId,
            @Size(max = 20) String telefono) {
    }

    public record UpdateUserRequest(
            @Size(min = 1, max = 150) String nombreCompleto,
            @Email(message = "correo no tiene un formato válido.") @Size(max = 150) String correo,
            @Size(max = 20) String telefono,
            String rol,
            String areaId,
            Boolean activo) {
    }

    public record UserResponse(String id, String nombreCompleto, String correo, String telefono, String rol,
            String areaId, boolean activo, String estadoCuenta, String origenRegistro, String creadoEn) {
    }

    /** Fila de la bandeja de cuentas por aprobar (HU-28). */
    public record PendingAccountResponse(String id, String nombreCompleto, String correo, String creadoEn,
            String estadoCuenta) {
    }

    public enum EstadoDecision { ACTIVA, RECHAZADA }

    public record AccountDecisionRequest(
            @NotNull(message = "estadoCuenta es obligatorio.") EstadoDecision estadoCuenta,
            @Size(max = 500) String motivo) {
    }

    /** Contraseña temporal mostrada una sola vez al administrador (HU-29). */
    public record PasswordResetResponse(String contrasenaTemporal) {
    }

    public record RoleResponse(String codigo, String nombre) {
    }

    public record EligibleTechnicianResponse(String id, String nombreCompleto, String areaId) {
    }
}
