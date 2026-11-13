package pe.edu.universidad.servicios.users.service;

import java.time.Clock;
import java.util.Objects;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.universidad.servicios.audit.service.AuditEventService;
import pe.edu.universidad.servicios.catalogs.service.AreaCatalogService;
import pe.edu.universidad.servicios.shared.dto.PageQuery;
import pe.edu.universidad.servicios.shared.dto.PageResponse;
import pe.edu.universidad.servicios.shared.error.ApiException;
import pe.edu.universidad.servicios.shared.security.AuthenticatedActor;
import pe.edu.universidad.servicios.shared.security.RoleCode;
import pe.edu.universidad.servicios.shared.security.SessionRevocation;
import pe.edu.universidad.servicios.shared.web.Ids;
import pe.edu.universidad.servicios.shared.web.Patch;
import pe.edu.universidad.servicios.shared.web.Times;
import pe.edu.universidad.servicios.users.dto.UserDtos.CreateUserRequest;
import pe.edu.universidad.servicios.users.dto.UserDtos.UpdateUserRequest;
import pe.edu.universidad.servicios.users.dto.UserDtos.UserResponse;
import pe.edu.universidad.servicios.users.repository.UserRecord;
import pe.edu.universidad.servicios.users.repository.UserRepository;

/**
 * Alta y edición administrativa de cuentas institucionales.
 */
@Service
public class UserAdminService {

    private final UserRepository users;
    private final AreaCatalogService areas;
    private final PasswordEncoder encoder;
    private final SessionRevocation revocation;
    private final AuditEventService audit;
    private final Clock clock;

    public UserAdminService(UserRepository users, AreaCatalogService areas, PasswordEncoder encoder,
            SessionRevocation revocation, AuditEventService audit, Clock clock) {
        this.users = users;
        this.areas = areas;
        this.encoder = encoder;
        this.revocation = revocation;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> list(AuthenticatedActor actor, PageQuery page) {
        actor.requireRole(RoleCode.ADMINISTRADOR);
        return new PageResponse<>(users.page(page).stream().map(UserAdminService::toResponse).toList(),
                page.page(), page.size(), users.count());
    }

    @Transactional(readOnly = true)
    public UserResponse detail(AuthenticatedActor actor, long id) {
        actor.requireRole(RoleCode.ADMINISTRADOR);
        return users.findById(id).map(UserAdminService::toResponse)
                .orElseThrow(() -> ApiException.notFound("La cuenta no existe."));
    }

    @Transactional
    public UserResponse create(AuthenticatedActor actor, CreateUserRequest body) {
        actor.requireRole(RoleCode.ADMINISTRADOR);
        RoleCode role = parseRole(body.rol());
        Long areaId = resolveArea(role, Ids.parse("areaId", body.areaId()), null);
        UserIdentityService.validatePasswordPolicy("contrasenaInicial", body.contrasenaInicial());
        String correo = UserIdentityService.normalizeEmail(body.correo());
        if (users.emailExists(correo, null)) {
            throw duplicateEmail();
        }
        try {
            long id = users.insert(role, areaId, body.nombreCompleto().trim(), correo,
                    encoder.encode(body.contrasenaInicial()), Patch.trimToNull(body.telefono()), clock.instant());
            audit.record("usuarios", id, null, "CREAR", "rol", null, role.name(), actor.userId());
            return toResponse(users.findById(id).orElseThrow());
        } catch (DuplicateKeyException ex) {
            throw duplicateEmail();
        }
    }

    @Transactional
    public UserResponse update(AuthenticatedActor actor, long id, UpdateUserRequest body) {
        actor.requireRole(RoleCode.ADMINISTRADOR);
        Patch.requireAny(body.nombreCompleto(), body.correo(), body.telefono(), body.rol(), body.areaId(), body.activo());
        UserRecord current = users.lockById(id).orElseThrow(() -> ApiException.notFound("La cuenta no existe."));
        RoleCode role = body.rol() != null ? parseRole(body.rol()) : current.role();
        boolean activo = Patch.or(body.activo(), current.activo());
        if (id == actor.userId() && (role != current.role() || !activo)) {
            throw ApiException.forbidden("OPERACION_SOBRE_CUENTA_PROPIA",
                    "Un administrador no puede cambiar su propio rol ni desactivar su propia cuenta.");
        }
        Long requestedArea = Ids.parse("areaId", body.areaId());
        Long areaId = resolveArea(role, requestedArea != null ? requestedArea : current.areaId(), current.areaId());
        String correo = body.correo() != null ? UserIdentityService.normalizeEmail(body.correo()) : current.correo();
        if (!correo.equals(current.correo()) && users.emailExists(correo, id)) {
            throw duplicateEmail();
        }
        String nombre = Patch.text("nombreCompleto", body.nombreCompleto(), current.nombreCompleto());
        String telefono = Patch.optionalText(body.telefono(), current.telefono());
        try {
            users.update(id, role, areaId, nombre, correo, telefono, activo);
        } catch (DuplicateKeyException ex) {
            throw duplicateEmail();
        }
        if (current.activo() && !activo) {
            revocation.revokeAllForUser(id);
        }
        auditChange(actor, id, "rol", current.role().name(), role.name());
        auditChange(actor, id, "areaId", String.valueOf(current.areaId()), String.valueOf(areaId));
        auditChange(actor, id, "activo", String.valueOf(current.activo()), String.valueOf(activo));
        auditChange(actor, id, "correo", current.correo(), correo);
        return toResponse(users.findById(id).orElseThrow());
    }

    private void auditChange(AuthenticatedActor actor, long id, String campo, String anterior, String nuevo) {
        if (!Objects.equals(anterior, nuevo)) {
            audit.record("usuarios", id, null, "ACTUALIZAR", campo, anterior, nuevo, actor.userId());
        }
    }

    /**
     * Técnico y supervisor requieren un área activa; los demás roles no se asocian a un área.
     * Un área ya asignada que se desactivó se conserva si no cambia.
     */
    private Long resolveArea(RoleCode role, Long areaId, Long currentArea) {
        if (!role.requiresArea()) {
            return null;
        }
        if (areaId == null) {
            throw ApiException.invalidField("areaId", "areaId es obligatorio para el rol " + role.name() + ".");
        }
        if (Objects.equals(areaId, currentArea) && areas.find(areaId).isPresent()) {
            return areaId;
        }
        return areas.findActive(areaId)
                .orElseThrow(() -> ApiException.invalidField("areaId", "El área no existe o no está activa."))
                .id();
    }

    private static RoleCode parseRole(String rol) {
        return RoleCode.parse(rol).orElseThrow(() -> ApiException.invalidField("rol", "El rol no es válido."));
    }

    private static ApiException duplicateEmail() {
        return ApiException.conflict("CORREO_DUPLICADO", "Ya existe una cuenta con ese correo.");
    }

    static UserResponse toResponse(UserRecord u) {
        return new UserResponse(Ids.str(u.id()), u.nombreCompleto(), u.correo(), u.telefono(), u.role().name(),
                Ids.str(u.areaId()), u.activo(), u.estadoCuenta(), u.origenRegistro(), Times.iso(u.creadoEn()));
    }
}
