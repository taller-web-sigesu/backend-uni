package pe.edu.universidad.servicios.auth.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import pe.edu.universidad.servicios.shared.error.ApiException;
import pe.edu.universidad.servicios.shared.security.AuthenticatedActor;
import pe.edu.universidad.servicios.shared.security.SessionRevocation;
import pe.edu.universidad.servicios.users.repository.UserRecord;
import pe.edu.universidad.servicios.users.service.UserIdentityService;

/**
 * Cambia la contraseña, revoca sesiones previas y crea la nueva sesión en una sola transacción.
 */
@Service
public class PasswordChangeService {

    private final UserIdentityService identity;
    private final SessionRevocation revocation;
    private final SessionService sessions;
    private final TransactionTemplate tx;

    public PasswordChangeService(UserIdentityService identity, SessionRevocation revocation, SessionService sessions,
            TransactionTemplate tx) {
        this.identity = identity;
        this.revocation = revocation;
        this.sessions = sessions;
        this.tx = tx;
    }

    public IssuedSession change(AuthenticatedActor actor, String current, String next, String ip, String userAgent) {
        if (current == null || current.isEmpty()) {
            throw ApiException.invalidField("contrasenaActual", "contrasenaActual es obligatoria.");
        }
        UserIdentityService.validatePasswordPolicy("contrasenaNueva", next);
        if (next.equals(current)) {
            throw ApiException.invalidField("contrasenaNueva", "La contraseña nueva debe ser distinta de la actual.");
        }
        return tx.execute(status -> {
            UserRecord user = identity.lock(actor.userId())
                    .filter(UserRecord::canLogin)
                    .orElseThrow(SessionService::invalidSession);
            if (!identity.passwordMatches(user, current)) {
                throw ApiException.invalidField("contrasenaActual", "La contraseña actual no es correcta.");
            }
            identity.changePassword(user.id(), next);
            revocation.revokeAllForUser(user.id());
            UserRecord updated = identity.find(user.id()).orElseThrow();
            return sessions.createSession(updated, ip, userAgent);
        });
    }
}
