package pe.edu.universidad.servicios.auth.service;

import java.time.Instant;

import pe.edu.universidad.servicios.users.repository.UserRecord;

/**
 * Credenciales emitidas tras confirmar la transacción de sesión.
 */
public record IssuedSession(String accessToken, long accessTtlSeconds, String refreshToken, Instant refreshExpiresAt,
        UserRecord user) {
}
