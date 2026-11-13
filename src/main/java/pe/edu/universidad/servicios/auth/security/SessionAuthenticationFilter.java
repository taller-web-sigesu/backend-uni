package pe.edu.universidad.servicios.auth.security;

import java.io.IOException;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import pe.edu.universidad.servicios.auth.repository.UserSessionRepository;
import pe.edu.universidad.servicios.auth.repository.UserSessionRepository.SessionActorRow;
import pe.edu.universidad.servicios.shared.security.AuthenticatedActor;
import pe.edu.universidad.servicios.shared.web.ProblemResponseWriter;

/**
 * Autentica el JWT y comprueba en cada petición que la sesión y la cuenta sigan vigentes.
 * Con cambio de contraseña pendiente solo permite cambio, renovación y cierre de sesión.
 */
public class SessionAuthenticationFilter extends OncePerRequestFilter {

    private static final Set<String> FIRST_LOGIN_PATHS = Set.of(
            "/api/v1/auth/password", "/api/v1/auth/logout", "/api/v1/auth/refresh", "/api/v1/auth/login");

    private final AccessTokenService tokens;
    private final UserSessionRepository sessions;
    private final ProblemResponseWriter problems;
    private final Clock clock;

    public SessionAuthenticationFilter(AccessTokenService tokens, UserSessionRepository sessions,
            ProblemResponseWriter problems, Clock clock) {
        this.tokens = tokens;
        this.sessions = sessions;
        this.problems = problems;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            Optional<AuthenticatedActor> actor = tokens.verify(header.substring(7).trim()).flatMap(claims ->
                    sessions.findActor(claims.sessionId())
                            .filter(row -> row.userId() == claims.userId())
                            .filter(this::isValid)
                            .map(SessionAuthenticationFilter::toActor));
            if (actor.isPresent()) {
                AuthenticatedActor current = actor.get();
                if (current.mustChangePassword() && !FIRST_LOGIN_PATHS.contains(pathOf(request))) {
                    problems.write(response, HttpStatus.FORBIDDEN, "CAMBIO_CONTRASENA_REQUERIDO",
                            "Debe cambiar su contraseña antes de continuar.");
                    return;
                }
                var authentication = new UsernamePasswordAuthenticationToken(current, null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + current.role().name())));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }
        chain.doFilter(request, response);
    }

    private boolean isValid(SessionActorRow row) {
        return row.revocadaEn() == null
                && row.expiraEn().isAfter(clock.instant())
                && row.activo()
                && "ACTIVA".equals(row.estadoCuenta());
    }

    private static AuthenticatedActor toActor(SessionActorRow row) {
        return new AuthenticatedActor(row.userId(), row.sessionId(), row.role(), row.areaId(), row.nombreCompleto(),
                row.debeCambiarContrasena());
    }

    private static String pathOf(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String context = request.getContextPath();
        return context != null && !context.isEmpty() && uri.startsWith(context) ? uri.substring(context.length()) : uri;
    }
}
