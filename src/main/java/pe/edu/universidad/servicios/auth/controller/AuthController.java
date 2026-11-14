package pe.edu.universidad.servicios.auth.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import pe.edu.universidad.servicios.auth.dto.AuthDtos.ChangePasswordRequest;
import pe.edu.universidad.servicios.auth.dto.AuthDtos.LoginRequest;
import pe.edu.universidad.servicios.auth.dto.AuthDtos.RegisterRequest;
import pe.edu.universidad.servicios.auth.dto.AuthDtos.RegisterResponse;
import pe.edu.universidad.servicios.auth.dto.AuthDtos.SessionResponse;
import pe.edu.universidad.servicios.auth.dto.AuthDtos.SessionUserResponse;
import pe.edu.universidad.servicios.auth.security.OriginGuard;
import pe.edu.universidad.servicios.auth.security.RefreshCookieFactory;
import pe.edu.universidad.servicios.auth.service.IssuedSession;
import pe.edu.universidad.servicios.auth.service.PasswordChangeService;
import pe.edu.universidad.servicios.auth.service.SessionService;
import pe.edu.universidad.servicios.shared.error.ApiException;
import pe.edu.universidad.servicios.shared.security.AuthenticatedActor;
import pe.edu.universidad.servicios.shared.security.CurrentActor;
import pe.edu.universidad.servicios.shared.web.Ids;
import pe.edu.universidad.servicios.users.service.SelfRegistrationService;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Acceso y sesión", description = "HU-01 y HU-02")
public class AuthController {

    private final SessionService sessions;
    private final PasswordChangeService passwords;
    private final RefreshCookieFactory cookies;
    private final OriginGuard origins;
    private final SelfRegistrationService registration;

    public AuthController(SessionService sessions, PasswordChangeService passwords, RefreshCookieFactory cookies,
            OriginGuard origins, SelfRegistrationService registration) {
        this.registration = registration;
        this.sessions = sessions;
        this.passwords = passwords;
        this.cookies = cookies;
        this.origins = origins;
    }

    @PostMapping("/login")
    @Operation(summary = "Inicia sesión con la cuenta institucional")
    public ResponseEntity<SessionResponse> login(@Valid @RequestBody LoginRequest body, HttpServletRequest request) {
        origins.check(request);
        IssuedSession issued = sessions.login(body.correo(), body.contrasena(), request.getRemoteAddr(),
                request.getHeader(HttpHeaders.USER_AGENT));
        return withSession(issued);
    }

    @PostMapping("/register")
    @Operation(summary = "Registra una cuenta de estudiante pendiente de aprobación (HU-27)")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest body, HttpServletRequest request) {
        origins.check(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(registration.register(body, request.getRemoteAddr()));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rota el refresh y emite un JWT nuevo")
    public ResponseEntity<SessionResponse> refresh(
            @CookieValue(name = RefreshCookieFactory.NAME, required = false) String refreshToken,
            HttpServletRequest request) {
        origins.check(request);
        return withSession(sessions.refresh(refreshToken));
    }

    @PostMapping("/logout")
    @Operation(summary = "Revoca la sesión actual")
    public ResponseEntity<Void> logout(
            @CookieValue(name = RefreshCookieFactory.NAME, required = false) String refreshToken,
            HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedActor actor) {
            sessions.logoutSession(actor.sessionId());
        } else if (refreshToken != null && !refreshToken.isBlank()) {
            origins.check(request);
            sessions.logoutWithRefresh(refreshToken);
        } else {
            throw ApiException.unauthorized("SESION_INVALIDA", "Se requiere una credencial de sesión válida.");
        }
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookies.clear().toString())
                .cacheControl(CacheControl.noStore())
                .build();
    }

    @PatchMapping("/password")
    @Operation(summary = "Cambia la contraseña propia y reemplaza la sesión")
    public ResponseEntity<SessionResponse> changePassword(@CurrentActor AuthenticatedActor actor,
            @Valid @RequestBody ChangePasswordRequest body, HttpServletRequest request) {
        IssuedSession issued = passwords.change(actor, body.contrasenaActual(), body.contrasenaNueva(),
                request.getRemoteAddr(), request.getHeader(HttpHeaders.USER_AGENT));
        return withSession(issued);
    }

    private ResponseEntity<SessionResponse> withSession(IssuedSession issued) {
        var user = issued.user();
        SessionResponse body = new SessionResponse(issued.accessToken(), issued.accessTtlSeconds(),
                new SessionUserResponse(Ids.str(user.id()), user.nombreCompleto(), user.role().name(), Ids.str(user.areaId()),
                        user.areaNombre()),
                user.debeCambiarContrasena());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookies.create(issued.refreshToken(), issued.refreshExpiresAt()).toString())
                .cacheControl(CacheControl.noStore())
                .body(body);
    }
}
