package pe.edu.universidad.servicios.users.service;

import java.time.Clock;
import java.time.Duration;

import org.springframework.stereotype.Component;

import pe.edu.universidad.servicios.shared.config.AppProperties;
import pe.edu.universidad.servicios.shared.security.SlidingWindowLimiter;

/**
 * Límite de solicitudes de auto-registro por IP en una hora (HU-27), en memoria de la instancia.
 */
@Component
public class RegistrationRateLimiter extends SlidingWindowLimiter {

    public RegistrationRateLimiter(AppProperties properties, Clock clock) {
        super(properties.registration().maxPerIpPerHour(), Duration.ofHours(1), clock);
    }
}
