package pe.edu.universidad.servicios.users.service;

import java.time.Clock;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.universidad.servicios.audit.service.AuditEventService;
import pe.edu.universidad.servicios.shared.dto.PageQuery;
import pe.edu.universidad.servicios.shared.dto.PageResponse;
import pe.edu.universidad.servicios.shared.error.ApiException;
import pe.edu.universidad.servicios.shared.security.AuthenticatedActor;
import pe.edu.universidad.servicios.shared.security.RoleCode;
import pe.edu.universidad.servicios.shared.web.Ids;
import pe.edu.universidad.servicios.shared.web.Times;
import pe.edu.universidad.servicios.users.dto.UserDtos.AccountDecisionRequest;
import pe.edu.universidad.servicios.users.dto.UserDtos.EstadoDecision;
import pe.edu.universidad.servicios.users.dto.UserDtos.PendingAccountResponse;
import pe.edu.universidad.servicios.users.dto.UserDtos.UserResponse;
import pe.edu.universidad.servicios.users.repository.UserRecord;
import pe.edu.universidad.servicios.users.repository.UserRepository;

/**
 * Bandeja y decisión sobre cuentas auto-registradas (HU-28). {@code RECHAZADA} solo puede pasar a {@code ACTIVA}.
 */
@Service
public class AccountReviewService {

    private final UserRepository users;
    private final AuditEventService audit;
    private final Clock clock;

    public AccountReviewService(UserRepository users, AuditEventService audit, Clock clock) {
        this.users = users;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<PendingAccountResponse> pending(AuthenticatedActor actor, PageQuery page) {
        actor.requireRole(RoleCode.ADMINISTRADOR);
        return new PageResponse<>(users.pending(page).stream()
                .map(u -> new PendingAccountResponse(Ids.str(u.id()), u.nombreCompleto(), u.correo(),
                        Times.iso(u.creadoEn()), u.estadoCuenta()))
                .toList(), page.page(), page.size(), users.countPending());
    }

    @Transactional
    public UserResponse decide(AuthenticatedActor actor, long id, AccountDecisionRequest body) {
        actor.requireRole(RoleCode.ADMINISTRADOR);
        String motivo = body.motivo() == null ? null : body.motivo().trim();
        boolean reject = body.estadoCuenta() == EstadoDecision.RECHAZADA;
        if (reject && (motivo == null || motivo.isEmpty())) {
            throw ApiException.invalidField("motivo", "El motivo es obligatorio para rechazar una cuenta.");
        }
        UserRecord current = users.lockById(id).orElseThrow(() -> ApiException.notFound("La cuenta no existe."));
        boolean allowed = "PENDIENTE".equals(current.estadoCuenta())
                || (!reject && "RECHAZADA".equals(current.estadoCuenta()));
        if (!allowed) {
            throw ApiException.conflict("CUENTA_NO_PENDIENTE", "La cuenta no está pendiente de aprobación.");
        }
        String nuevo = body.estadoCuenta().name();
        users.review(id, nuevo, actor.userId(), clock.instant(), reject ? motivo : null);
        audit.record("usuarios", id, null, reject ? "RECHAZAR_CUENTA" : "APROBAR_CUENTA", "estado_cuenta",
                current.estadoCuenta(), reject ? nuevo + ": " + motivo : nuevo, actor.userId());
        return UserAdminService.toResponse(users.findById(id).orElseThrow());
    }
}
