package pe.edu.universidad.servicios.auth.service;

import java.time.Clock;

import org.springframework.stereotype.Service;

import pe.edu.universidad.servicios.auth.repository.UserSessionRepository;
import pe.edu.universidad.servicios.shared.security.SessionRevocation;

/**
 * Revoca todas las sesiones de una cuenta dentro de la transacción del llamador.
 */
@Service
public class SessionRevocationService implements SessionRevocation {

    private final UserSessionRepository sessions;
    private final Clock clock;

    public SessionRevocationService(UserSessionRepository sessions, Clock clock) {
        this.sessions = sessions;
        this.clock = clock;
    }

    @Override
    public void revokeAllForUser(long userId) {
        sessions.revokeAllForUser(userId, clock.instant());
    }
}
