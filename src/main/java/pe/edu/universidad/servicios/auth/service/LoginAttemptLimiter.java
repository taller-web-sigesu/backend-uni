package pe.edu.universidad.servicios.auth.service;

import java.time.Clock;

import org.springframework.stereotype.Component;

import pe.edu.universidad.servicios.shared.config.AppProperties;
import pe.edu.universidad.servicios.shared.security.SlidingWindowLimiter;

/**
 * Límite de intentos fallidos de login por correo e IP en una ventana deslizante (memoria de la instancia).
 */
@Component
public class LoginAttemptLimiter extends SlidingWindowLimiter {

    public LoginAttemptLimiter(AppProperties properties, Clock clock) {
        super(properties.security().loginMaxAttempts(), properties.security().loginWindow(), clock);
    }

    public void recordFailure(String key) {
        record(key);
    }
}
