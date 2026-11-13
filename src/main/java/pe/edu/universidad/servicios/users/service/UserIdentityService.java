package pe.edu.universidad.servicios.users.service;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import pe.edu.universidad.servicios.shared.error.ApiException;
import pe.edu.universidad.servicios.users.repository.UserRecord;
import pe.edu.universidad.servicios.users.repository.UserRepository;

/**
 * Consulta acotada de identidad para {@code auth} y {@code reservations}.
 */
@Service
public class UserIdentityService {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    /** Hash de referencia para igualar el tiempo de respuesta cuando la cuenta no existe. */
    private final String dummyHash;

    public UserIdentityService(UserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
        this.dummyHash = encoder.encode("cuenta-inexistente-" + System.nanoTime());
    }

    public static String normalizeEmail(String correo) {
        return correo == null ? null : correo.trim().toLowerCase(Locale.ROOT);
    }

    /** Resultado de validar credenciales. El estado de la cuenta solo se revela si la contraseña coincide. */
    public sealed interface AuthenticationResult {
        record Authenticated(UserRecord user) implements AuthenticationResult {
        }

        record Pending() implements AuthenticationResult {
        }

        record Rejected() implements AuthenticationResult {
        }

        record Invalid() implements AuthenticationResult {
        }
    }

    public AuthenticationResult authenticate(String correo, String contrasena) {
        Optional<UserRecord> found = users.findByEmail(normalizeEmail(correo));
        if (found.isEmpty()) {
            encoder.matches(contrasena == null ? "" : contrasena, dummyHash);
            return new AuthenticationResult.Invalid();
        }
        UserRecord user = found.get();
        if (!encoder.matches(contrasena, user.hashContrasena()) || !user.activo()) {
            return new AuthenticationResult.Invalid();
        }
        return switch (user.estadoCuenta()) {
            case "ACTIVA" -> new AuthenticationResult.Authenticated(user);
            case "PENDIENTE" -> new AuthenticationResult.Pending();
            case "RECHAZADA" -> new AuthenticationResult.Rejected();
            default -> new AuthenticationResult.Invalid();
        };
    }

    public Optional<UserRecord> lock(long userId) {
        return users.lockById(userId);
    }

    public Optional<UserRecord> find(long userId) {
        return users.findById(userId);
    }

    public boolean passwordMatches(UserRecord user, String raw) {
        return encoder.matches(raw, user.hashContrasena());
    }

    public void changePassword(long userId, String newRaw) {
        users.updatePassword(userId, encoder.encode(newRaw), false);
    }

    public void touchLastAccess(long userId, Instant now) {
        users.touchLastAccess(userId, now);
    }

    /** Política mínima: 8 a 72 caracteres, con al menos una letra y un dígito. */
    public static void validatePasswordPolicy(String field, String raw) {
        if (raw == null || raw.length() < 8 || raw.length() > 72
                || raw.chars().noneMatch(Character::isLetter) || raw.chars().noneMatch(Character::isDigit)) {
            throw ApiException.invalidField(field,
                    "La contraseña debe tener entre 8 y 72 caracteres e incluir letras y números.");
        }
    }
}
