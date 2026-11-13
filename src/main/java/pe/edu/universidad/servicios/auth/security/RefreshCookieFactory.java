package pe.edu.universidad.servicios.auth.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import pe.edu.universidad.servicios.shared.config.AppProperties;

/**
 * Cookie HttpOnly del refresh, con el mismo alcance al crearla y al eliminarla.
 */
@Component
public class RefreshCookieFactory {

    public static final String NAME = "refresh_token";
    public static final String PATH = "/api/v1/auth";

    private final boolean secure;
    private final Clock clock;

    public RefreshCookieFactory(AppProperties properties, Clock clock) {
        this.secure = properties.security().cookieSecure();
        this.clock = clock;
    }

    public ResponseCookie create(String token, Instant expiresAt) {
        long maxAge = Math.max(0, Duration.between(clock.instant(), expiresAt).toSeconds());
        return base(token).maxAge(maxAge).build();
    }

    public ResponseCookie clear() {
        return base("").maxAge(0).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(NAME, value).httpOnly(true).secure(secure).sameSite("Lax").path(PATH);
    }
}
