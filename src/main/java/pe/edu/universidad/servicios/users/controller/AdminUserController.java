package pe.edu.universidad.servicios.users.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import pe.edu.universidad.servicios.shared.dto.PageQuery;
import pe.edu.universidad.servicios.shared.dto.PageResponse;
import pe.edu.universidad.servicios.shared.security.AuthenticatedActor;
import pe.edu.universidad.servicios.shared.security.CurrentActor;
import pe.edu.universidad.servicios.users.dto.UserDtos.AccountDecisionRequest;
import pe.edu.universidad.servicios.users.dto.UserDtos.CreateUserRequest;
import pe.edu.universidad.servicios.users.dto.UserDtos.PasswordResetResponse;
import pe.edu.universidad.servicios.users.dto.UserDtos.PendingAccountResponse;
import pe.edu.universidad.servicios.users.dto.UserDtos.RoleResponse;
import pe.edu.universidad.servicios.users.dto.UserDtos.UpdateUserRequest;
import pe.edu.universidad.servicios.users.dto.UserDtos.UserResponse;
import pe.edu.universidad.servicios.users.service.AccountReviewService;
import pe.edu.universidad.servicios.users.service.PasswordResetService;
import pe.edu.universidad.servicios.users.service.RoleQueryService;
import pe.edu.universidad.servicios.users.service.UserAdminService;

@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Usuarios y roles", description = "HU-15, HU-28 y HU-29")
public class AdminUserController {

    private final UserAdminService users;
    private final RoleQueryService roles;
    private final AccountReviewService reviews;
    private final PasswordResetService resets;

    public AdminUserController(UserAdminService users, RoleQueryService roles, AccountReviewService reviews,
            PasswordResetService resets) {
        this.users = users;
        this.roles = roles;
        this.reviews = reviews;
        this.resets = resets;
    }

    @GetMapping("/users/pending")
    @Operation(summary = "Lista las cuentas auto-registradas pendientes de aprobación, de la más antigua a la más reciente (HU-28)")
    public PageResponse<PendingAccountResponse> pending(@CurrentActor AuthenticatedActor actor,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return reviews.pending(actor, PageQuery.of(page, size));
    }

    @PatchMapping("/users/{id}/account-status")
    @Operation(summary = "Aprueba o rechaza (con motivo) una cuenta auto-registrada (HU-28)")
    public UserResponse decide(@CurrentActor AuthenticatedActor actor, @PathVariable long id,
            @Valid @RequestBody AccountDecisionRequest body) {
        return reviews.decide(actor, id, body);
    }

    @PostMapping("/users/{id}/password-reset")
    @Operation(summary = "Genera una contraseña temporal, revoca las sesiones y exige cambiarla (HU-29)")
    public ResponseEntity<PasswordResetResponse> resetPassword(@CurrentActor AuthenticatedActor actor, @PathVariable long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(resets.reset(actor, id));
    }

    @GetMapping("/users")
    @Operation(summary = "Lista cuentas")
    public PageResponse<UserResponse> list(@CurrentActor AuthenticatedActor actor,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return users.list(actor, PageQuery.of(page, size));
    }

    @GetMapping("/users/{id}")
    @Operation(summary = "Consulta una cuenta")
    public UserResponse detail(@CurrentActor AuthenticatedActor actor, @PathVariable long id) {
        return users.detail(actor, id);
    }

    @PostMapping("/users")
    @Operation(summary = "Crea una cuenta institucional con cambio de contraseña obligatorio")
    public ResponseEntity<UserResponse> create(@CurrentActor AuthenticatedActor actor, @Valid @RequestBody CreateUserRequest body) {
        UserResponse created = users.create(actor, body);
        return ResponseEntity.created(URI.create("/api/v1/admin/users/" + created.id())).body(created);
    }

    @PatchMapping("/users/{id}")
    @Operation(summary = "Edita datos, rol o área, o desactiva una cuenta")
    public UserResponse update(@CurrentActor AuthenticatedActor actor, @PathVariable long id,
            @Valid @RequestBody UpdateUserRequest body) {
        return users.update(actor, id, body);
    }

    @GetMapping("/roles")
    @Operation(summary = "Consulta los roles disponibles")
    public List<RoleResponse> roles(@CurrentActor AuthenticatedActor actor) {
        return roles.list(actor);
    }
}
