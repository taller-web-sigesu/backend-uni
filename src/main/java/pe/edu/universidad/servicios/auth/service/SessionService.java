package pe.edu.universidad.servicios.auth.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import pe.edu.universidad.servicios.auth.repository.UserSessionRepository;
import pe.edu.universidad.servicios.auth.repository.UserSessionRepository.SessionRow;
import pe.edu.universidad.servicios.auth.security.AccessTokenService;
import pe.edu.universidad.servicios.auth.security.RefreshTokenCodec;
import pe.edu.universidad.servicios.shared.config.AppProperties;
import pe.edu.universidad.servicios.shared.error.ApiException;
import pe.edu.universidad.servicios.users.repository.UserRecord;
import pe.edu.universidad.servicios.users.service.UserIdentityService;
import pe.edu.universidad.servicios.users.service.UserIdentityService.AuthenticationResult;

/**
 * Valida, emite, rota y revoca sesiones. El orden de bloqueo es siempre cuenta y luego sesión.
 */
@Service
public class SessionService {

    private final UserIdentityService identity;
    private final UserSessionRepository sessions;
    private final AccessTokenService accessTokens;
    private final LoginAttemptLimiter limiter;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final Duration refreshTtl;

    public SessionService(UserIdentityService identity, UserSessionRepository sessions, AccessTokenService accessTokens,
            LoginAttemptLimiter limiter, TransactionTemplate tx, Clock clock, AppProperties properties) {
        this.identity = identity;
        this.sessions = sessions;
        this.accessTokens = accessTokens;
        this.limiter = limiter;
        this.tx = tx;
        this.clock = clock;
        this.refreshTtl = properties.security().refreshTokenTtl();
    }

    public IssuedSession login(String correo, String contrasena, String ip, String userAgent) {
        String key = UserIdentityService.normalizeEmail(correo) + "|" + ip;
        if (limiter.isBlocked(key)) {
            throw ApiException.tooManyRequests("Se superó el número de intentos. Intente más tarde.");
        }
        long userId = switch (identity.authenticate(correo, contrasena)) {
            case AuthenticationResult.Authenticated ok -> ok.user().id();
            case AuthenticationResult.Pending p -> throw ApiException.forbidden("CUENTA_PENDIENTE",
                    "Tu cuenta está pendiente de aprobación por un administrador.");
            case AuthenticationResult.Rejected r -> throw ApiException.forbidden("CUENTA_RECHAZADA",
                    "Tu solicitud de cuenta fue rechazada. Comunícate con la administración.");
            case AuthenticationResult.Invalid i -> {
                limiter.recordFailure(key);
                throw invalidCredentials();
            }
        };
        limiter.reset(key);
        return tx.execute(status -> {
            // La cuenta se vuelve a leer bloqueada para no emitir con datos obsoletos ante una desactivación concurrente.
            UserRecord user = identity.lock(userId).filter(UserRecord::canLogin).orElseThrow(SessionService::invalidCredentials);
            identity.touchLastAccess(userId, clock.instant());
            return createSession(user, ip, userAgent);
        });
    }

    /** Debe ejecutarse dentro de una transacción con la cuenta bloqueada. */
    public IssuedSession createSession(UserRecord user, String ip, String userAgent) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(refreshTtl);
        String refresh = RefreshTokenCodec.generate();
        long sessionId = sessions.insert(user.id(), RefreshTokenCodec.hash(refresh), now, expiresAt, truncate(ip, 45),
                truncate(userAgent, 255));
        return new IssuedSession(accessTokens.issue(user.id(), sessionId), accessTokens.ttlSeconds(), refresh, expiresAt, user);
    }

    public IssuedSession refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw invalidSession();
        }
        String hash = RefreshTokenCodec.hash(refreshToken);
        RefreshOutcome outcome = tx.execute(status -> rotate(hash));
        if (outcome.session() != null) {
            return outcome.session();
        }
        if (outcome.reusedSessionId() != null) {
            // La revocación por reúso se confirma en su propia transacción antes de responder 401.
            tx.executeWithoutResult(status -> sessions.revoke(outcome.reusedSessionId(), clock.instant()));
        }
        throw invalidSession();
    }

    private RefreshOutcome rotate(String hash) {
        Optional<SessionRow> found = sessions.findByHash(hash);
        if (found.isEmpty()) {
            return sessions.findSessionOfConsumed(hash)
                    .map(RefreshOutcome::reused)
                    .orElse(RefreshOutcome.rejected());
        }
        Instant now = clock.instant();
        Optional<UserRecord> user = identity.lock(found.get().userId());
        SessionRow session = sessions.lockById(found.get().id()).orElseThrow(SessionService::invalidSession);
        if (!session.hashToken().equals(hash)) {
            // Otra renovación ya consumió este token: se trata como reúso.
            return RefreshOutcome.reused(session.id());
        }
        if (!session.isActive(now)) {
            return RefreshOutcome.rejected();
        }
        if (user.isEmpty() || !user.get().canLogin()) {
            sessions.revoke(session.id(), now);
            return RefreshOutcome.rejected();
        }
        String next = RefreshTokenCodec.generate();
        sessions.rotate(session.id(), RefreshTokenCodec.hash(next));
        sessions.insertConsumed(session.id(), hash, now, session.expiraEn());
        // La rotación conserva el vencimiento absoluto original.
        IssuedSession issued = new IssuedSession(accessTokens.issue(user.get().id(), session.id()), accessTokens.ttlSeconds(),
                next, session.expiraEn(), user.get());
        return RefreshOutcome.rotated(issued);
    }

    public void logoutSession(long sessionId) {
        tx.executeWithoutResult(status -> sessions.revoke(sessionId, clock.instant()));
    }

    /** Logout con cookie: revoca la sesión vigente o la que emitió un token ya rotado. */
    public void logoutWithRefresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw invalidSession();
        }
        String hash = RefreshTokenCodec.hash(refreshToken);
        Instant now = clock.instant();
        Long sessionId = sessions.findByHash(hash)
                .filter(s -> s.isActive(now))
                .map(SessionRow::id)
                .orElse(null);
        if (sessionId == null) {
            sessions.findSessionOfConsumed(hash).ifPresent(id -> tx.executeWithoutResult(s -> sessions.revoke(id, now)));
            throw invalidSession();
        }
        logoutSession(sessionId);
    }

    public void purgeConsumed() {
        tx.executeWithoutResult(status -> sessions.purgeExpiredConsumed(clock.instant()));
    }

    static ApiException invalidCredentials() {
        return ApiException.unauthorized("CREDENCIALES_INVALIDAS", "Correo o contraseña incorrectos.");
    }

    static ApiException invalidSession() {
        return ApiException.unauthorized("SESION_INVALIDA", "La sesión no es válida o expiró.");
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    private record RefreshOutcome(IssuedSession session, Long reusedSessionId) {
        static RefreshOutcome rotated(IssuedSession session) {
            return new RefreshOutcome(session, null);
        }

        static RefreshOutcome reused(Long sessionId) {
            return new RefreshOutcome(null, sessionId);
        }

        static RefreshOutcome rejected() {
            return new RefreshOutcome(null, null);
        }
    }
}
