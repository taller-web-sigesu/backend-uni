package pe.edu.universidad.servicios.users.service;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.universidad.servicios.shared.config.AppProperties;
import pe.edu.universidad.servicios.shared.security.RoleCode;
import pe.edu.universidad.servicios.users.repository.UserRepository;

/**
 * Aprovisiona el primer administrador con credenciales de entorno, solo si aún no existe ninguno.
 * La cuenta queda obligada a cambiar la contraseña en su primer ingreso.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final AppProperties properties;
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final Clock clock;

    public AdminBootstrap(AppProperties properties, UserRepository users, PasswordEncoder encoder, Clock clock) {
        this.properties = properties;
        this.users = users;
        this.encoder = encoder;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        AppProperties.BootstrapAdmin admin = properties.bootstrapAdmin();
        if (admin == null || isBlank(admin.correo()) || isBlank(admin.contrasena())) {
            return;
        }
        if (users.adminExists()) {
            log.info("Aprovisionamiento omitido: ya existe una cuenta de administrador.");
            return;
        }
        String correo = UserIdentityService.normalizeEmail(admin.correo());
        if (users.emailExists(correo, null)) {
            log.warn("Aprovisionamiento omitido: el correo configurado ya pertenece a otra cuenta.");
            return;
        }
        UserIdentityService.validatePasswordPolicy("APP_BOOTSTRAP_ADMIN_PASSWORD", admin.contrasena());
        String nombre = isBlank(admin.nombre()) ? "Administrador inicial" : admin.nombre().trim();
        users.insert(RoleCode.ADMINISTRADOR, null, nombre, correo, encoder.encode(admin.contrasena()), null, clock.instant());
        log.info("Administrador inicial aprovisionado.");
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
