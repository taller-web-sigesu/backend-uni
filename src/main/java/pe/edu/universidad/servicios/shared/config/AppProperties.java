package pe.edu.universidad.servicios.shared.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(Security security, BootstrapAdmin bootstrapAdmin, Registration registration, Storage storage) {

    public record Security(
            String jwtSecret,
            Duration accessTokenTtl,
            Duration refreshTokenTtl,
            boolean cookieSecure,
            List<String> allowedOrigins,
            int loginMaxAttempts,
            Duration loginWindow) {
    }

    public record BootstrapAdmin(String correo, String contrasena, String nombre) {
    }

    /** Auto-registro de estudiantes (HU-27): dominio institucional exacto y límite de altas por IP. */
    public record Registration(String emailDomain, int maxPerIpPerHour) {
    }

    /** Almacenamiento de evidencias (HU-26): {@code local} (volumen) o {@code supabase} (Storage, Fase 5). */
    public record Storage(String provider, String localDir, Supabase supabase) {

        public record Supabase(String url, String serviceKey, String bucket) {
        }
    }
}
