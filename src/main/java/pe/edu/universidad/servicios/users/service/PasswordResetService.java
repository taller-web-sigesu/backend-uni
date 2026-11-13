package pe.edu.universidad.servicios.users.service;

import java.security.SecureRandom;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.universidad.servicios.audit.service.AuditEventService;
import pe.edu.universidad.servicios.shared.error.ApiException;
import pe.edu.universidad.servicios.shared.security.AuthenticatedActor;
import pe.edu.universidad.servicios.shared.security.RoleCode;
import pe.edu.universidad.servicios.shared.security.SessionRevocation;
import pe.edu.universidad.servicios.users.dto.UserDtos.PasswordResetResponse;
import pe.edu.universidad.servicios.users.repository.UserRecord;
import pe.edu.universidad.servicios.users.repository.UserRepository;

/**
 * Restablecimiento de contraseña por el administrador (HU-29): contraseña temporal, cambio obligatorio y
 * revocación de sesiones en una sola transacción. La temporal no se guarda ni se registra.
 */
@Service
public class PasswordResetService {

    /** Sin caracteres ambiguos (0/O, 1/l/I) para dictarla sin errores. */
    static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
    static final int LENGTH = 12;

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final SessionRevocation revocation;
    private final AuditEventService audit;
    private final SecureRandom random = new SecureRandom();

    public PasswordResetService(UserRepository users, PasswordEncoder encoder, SessionRevocation revocation,
            AuditEventService audit) {
        this.users = users;
        this.encoder = encoder;
        this.revocation = revocation;
        this.audit = audit;
    }

    @Transactional
    public PasswordResetResponse reset(AuthenticatedActor actor, long id) {
        actor.requireRole(RoleCode.ADMINISTRADOR);
        if (id == actor.userId()) {
            throw ApiException.forbidden("OPERACION_SOBRE_CUENTA_PROPIA",
                    "Para cambiar tu propia contraseña usa la opción de cambio de contraseña.");
        }
        UserRecord user = users.lockById(id).orElseThrow(() -> ApiException.notFound("La cuenta no existe."));
        if (!user.canLogin()) {
            throw ApiException.conflict("CUENTA_NO_ACTIVA", "Solo se restablece la contraseña de cuentas activas.");
        }
        String temporal = temporaryPassword();
        users.updatePassword(id, encoder.encode(temporal), true);
        revocation.revokeAllForUser(id);
        audit.record("usuarios", id, null, "RESTABLECER_CONTRASENA", "debe_cambiar_contrasena", null, "true", actor.userId());
        return new PasswordResetResponse(temporal);
    }

    String temporaryPassword() {
        char[] chars = new char[LENGTH];
        // Garantiza al menos una letra y un dígito (política de SECURITY.md); el resto es aleatorio.
        chars[0] = ALPHABET.charAt(random.nextInt(ALPHABET.length() - 8));
        chars[1] = ALPHABET.charAt(ALPHABET.length() - 8 + random.nextInt(8));
        for (int i = 2; i < LENGTH; i++) {
            chars[i] = ALPHABET.charAt(random.nextInt(ALPHABET.length()));
        }
        for (int i = LENGTH - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            char t = chars[i];
            chars[i] = chars[j];
            chars[j] = t;
        }
        return new String(chars);
    }
}
