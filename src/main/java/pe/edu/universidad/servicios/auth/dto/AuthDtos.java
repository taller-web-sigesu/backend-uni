package pe.edu.universidad.servicios.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Esquemas HTTP de acceso y sesión (HU-01 y HU-02).
 */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(
            @NotBlank(message = "correo es obligatorio.") @Size(max = 150) String correo,
            @NotBlank(message = "contrasena es obligatoria.") @Size(max = 200) String contrasena) {
    }

    public record ChangePasswordRequest(
            @NotBlank(message = "contrasenaActual es obligatoria.") String contrasenaActual,
            @NotBlank(message = "contrasenaNueva es obligatoria.") String contrasenaNueva) {
    }

    /** Campos desconocidos como {@code rol} se ignoran: el auto-registro siempre crea estudiantes. */
    public record RegisterRequest(
            @NotBlank(message = "nombreCompleto es obligatorio.") @Size(max = 150) String nombreCompleto,
            @NotBlank(message = "correo es obligatorio.") @Size(max = 160) String correo,
            @NotBlank(message = "contrasena es obligatoria.") String contrasena,
            @NotBlank(message = "confirmacionContrasena es obligatoria.") String confirmacionContrasena) {
    }

    public record RegisterResponse(String mensaje) {
    }

    public record SessionUserResponse(String id, String nombreCompleto, String rol, String areaId, String areaNombre) {
    }

    public record SessionResponse(String tokenAcceso, long expiraEnSegundos, SessionUserResponse usuario,
            boolean debeCambiarContrasena) {
    }
}
