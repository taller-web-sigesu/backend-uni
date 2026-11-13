package pe.edu.universidad.servicios.users.service;

import java.time.Clock;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.universidad.servicios.audit.service.AuditEventService;
import pe.edu.universidad.servicios.auth.dto.AuthDtos.RegisterRequest;
import pe.edu.universidad.servicios.auth.dto.AuthDtos.RegisterResponse;
import pe.edu.universidad.servicios.shared.config.AppProperties;
import pe.edu.universidad.servicios.shared.error.ApiException;
import pe.edu.universidad.servicios.users.repository.UserRepository;

/**
 * Auto-registro de estudiantes con correo institucional (HU-27). La cuenta queda pendiente de aprobación (HU-28).
 */
@Service
public class SelfRegistrationService {

    /** Parte local del correo: sin espacios ni otro '@', así un subdominio como {@code @fisi.unmsm.edu.pe} no pasa. */
    private static final Pattern LOCAL_PART = Pattern.compile("[a-z0-9._%+-]{1,64}");

    static final String MENSAJE = "Registro enviado. Podrás ingresar cuando un administrador apruebe tu cuenta";

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final AuditEventService audit;
    private final RegistrationRateLimiter limiter;
    private final Clock clock;
    private final String domainSuffix;

    public SelfRegistrationService(UserRepository users, PasswordEncoder encoder, AuditEventService audit,
            RegistrationRateLimiter limiter, AppProperties properties, Clock clock) {
        this.users = users;
        this.encoder = encoder;
        this.audit = audit;
        this.limiter = limiter;
        this.clock = clock;
        this.domainSuffix = "@" + properties.registration().emailDomain().trim().toLowerCase(Locale.ROOT);
    }

    @Transactional
    public RegisterResponse register(RegisterRequest body, String ip) {
        String key = "register|" + ip;
        if (limiter.isBlocked(key)) {
            throw ApiException.tooManyRequests("Se superó el número de registros desde esta conexión. Intenta más tarde.");
        }
        limiter.record(key);

        String correo = UserIdentityService.normalizeEmail(body.correo());
        if (correo.length() > 150 || !correo.endsWith(domainSuffix)
                || !LOCAL_PART.matcher(correo.substring(0, correo.length() - domainSuffix.length())).matches()) {
            throw ApiException.invalidField("correo", "Debes usar tu correo institucional " + domainSuffix + ".");
        }
        UserIdentityService.validatePasswordPolicy("contrasena", body.contrasena());
        if (!body.contrasena().equals(body.confirmacionContrasena())) {
            throw ApiException.invalidField("confirmacionContrasena", "La confirmación no coincide con la contraseña.");
        }
        if (users.emailExists(correo, null)) {
            throw alreadyRegistered();
        }
        long id;
        try {
            id = users.insertSelfRegistered(body.nombreCompleto().trim(), correo, encoder.encode(body.contrasena()),
                    clock.instant());
        } catch (DuplicateKeyException ex) {
            throw alreadyRegistered();
        }
        audit.record("usuarios", id, null, "REGISTRO", "estado_cuenta", null, "PENDIENTE", id);
        return new RegisterResponse(MENSAJE);
    }

    private static ApiException alreadyRegistered() {
        return ApiException.conflict("CORREO_REGISTRADO", "Ya existe una cuenta registrada con ese correo.");
    }
}
