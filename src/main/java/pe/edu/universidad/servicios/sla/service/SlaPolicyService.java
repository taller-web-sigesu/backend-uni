package pe.edu.universidad.servicios.sla.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.universidad.servicios.audit.service.AuditEventService;
import pe.edu.universidad.servicios.catalogs.service.CategoryCatalogService;
import pe.edu.universidad.servicios.catalogs.service.PriorityCatalogService;
import pe.edu.universidad.servicios.shared.dto.PageQuery;
import pe.edu.universidad.servicios.shared.dto.PageResponse;
import pe.edu.universidad.servicios.shared.error.ApiException;
import pe.edu.universidad.servicios.shared.security.AuthenticatedActor;
import pe.edu.universidad.servicios.shared.security.RoleCode;
import pe.edu.universidad.servicios.shared.web.Ids;
import pe.edu.universidad.servicios.shared.web.Times;
import pe.edu.universidad.servicios.sla.dto.SlaDtos.CreateSlaPolicyRequest;
import pe.edu.universidad.servicios.sla.dto.SlaDtos.SlaPolicyResponse;
import pe.edu.universidad.servicios.sla.dto.SlaDtos.UpdateSlaPolicyRequest;
import pe.edu.universidad.servicios.sla.repository.SlaPolicyRepository;
import pe.edu.universidad.servicios.sla.repository.SlaPolicyRepository.SlaPolicy;

/**
 * Administra políticas de SLA: como máximo una vigente por categoría o prioridad en cada instante.
 * Los cambios afectan solo a solicitudes nuevas; las existentes conservan sus plazos congelados.
 */
@Service
public class SlaPolicyService {

    private final SlaPolicyRepository policies;
    private final CategoryCatalogService categories;
    private final PriorityCatalogService priorities;
    private final AuditEventService audit;
    private final Clock clock;

    public SlaPolicyService(SlaPolicyRepository policies, CategoryCatalogService categories,
            PriorityCatalogService priorities, AuditEventService audit, Clock clock) {
        this.policies = policies;
        this.categories = categories;
        this.priorities = priorities;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<SlaPolicyResponse> list(AuthenticatedActor actor, PageQuery page) {
        actor.requireRole(RoleCode.ADMINISTRADOR);
        return new PageResponse<>(policies.page(page).stream().map(SlaPolicyService::toResponse).toList(),
                page.page(), page.size(), policies.count());
    }

    @Transactional
    public SlaPolicyResponse create(AuthenticatedActor actor, CreateSlaPolicyRequest body) {
        actor.requireRole(RoleCode.ADMINISTRADOR);
        Long categoriaId = Ids.parse("categoriaId", body.categoriaId());
        Long prioridadId = Ids.parse("prioridadId", body.prioridadId());
        if ((categoriaId == null) == (prioridadId == null)) {
            throw ApiException.badRequest("ALCANCE_INVALIDO", "Indique exactamente uno de categoriaId o prioridadId.");
        }
        if (categoriaId != null && categories.find(categoriaId).isEmpty()) {
            throw ApiException.invalidField("categoriaId", "La categoría no existe.");
        }
        if (prioridadId != null && priorities.find(prioridadId).isEmpty()) {
            throw ApiException.invalidField("prioridadId", "La prioridad no existe.");
        }
        Instant desde = body.vigenteDesde() != null ? body.vigenteDesde() : clock.instant();
        validate(body.horasObjetivo(), body.horasAviso(), desde, body.vigenteHasta());
        ensureNoOverlap(categoriaId, prioridadId, null, desde, body.vigenteHasta());
        long id = policies.insert(categoriaId, prioridadId, body.horasObjetivo(), body.horasAviso(), desde, body.vigenteHasta());
        audit.record("politicas_sla", id, null, "CREAR", "horasObjetivo", null, String.valueOf(body.horasObjetivo()), actor.userId());
        return toResponse(policies.findById(id).orElseThrow());
    }

    @Transactional
    public SlaPolicyResponse update(AuthenticatedActor actor, long id, UpdateSlaPolicyRequest body) {
        actor.requireRole(RoleCode.ADMINISTRADOR);
        if (body.categoriaId() != null || body.prioridadId() != null) {
            throw ApiException.badRequest("ALCANCE_INMUTABLE", "El alcance de la política no se puede modificar.");
        }
        SlaPolicy current = policies.findById(id).orElseThrow(() -> ApiException.notFound("La política no existe."));
        int objetivo = body.horasObjetivo() != null ? body.horasObjetivo() : current.horasObjetivo();
        int aviso = body.horasAviso() != null ? body.horasAviso() : current.horasAviso();
        Instant desde = body.vigenteDesde() != null ? body.vigenteDesde() : current.vigenteDesde();
        Instant hasta = body.vigenteHasta() != null ? body.vigenteHasta() : current.vigenteHasta();
        boolean activo = body.activo() != null ? body.activo() : current.activo();
        validate(objetivo, aviso, desde, hasta);
        if (activo) {
            ensureNoOverlap(current.categoriaId(), current.prioridadId(), id, desde, hasta);
        }
        policies.update(id, objetivo, aviso, desde, hasta, activo);
        auditChange(actor, id, "horasObjetivo", current.horasObjetivo(), objetivo);
        auditChange(actor, id, "horasAviso", current.horasAviso(), aviso);
        auditChange(actor, id, "activo", current.activo(), activo);
        return toResponse(policies.findById(id).orElseThrow());
    }

    private void auditChange(AuthenticatedActor actor, long id, String campo, Object anterior, Object nuevo) {
        if (!Objects.equals(anterior, nuevo)) {
            audit.record("politicas_sla", id, null, "ACTUALIZAR", campo, String.valueOf(anterior), String.valueOf(nuevo), actor.userId());
        }
    }

    private static void validate(int objetivo, int aviso, Instant desde, Instant hasta) {
        if (objetivo <= 0 || aviso < 0 || aviso >= objetivo) {
            throw ApiException.badRequest("PLAZOS_INVALIDOS", "horasAviso debe ser menor que horasObjetivo.");
        }
        if (hasta != null && !hasta.isAfter(desde)) {
            throw ApiException.badRequest("VIGENCIA_INVALIDA", "vigenteHasta debe ser posterior a vigenteDesde.");
        }
    }

    private void ensureNoOverlap(Long categoriaId, Long prioridadId, Long excludeId, Instant desde, Instant hasta) {
        boolean overlaps = policies.lockActiveSameScope(categoriaId, prioridadId).stream()
                .filter(p -> excludeId == null || p.id() != excludeId)
                .anyMatch(p -> overlaps(p.vigenteDesde(), p.vigenteHasta(), desde, hasta));
        if (overlaps) {
            throw ApiException.conflict("VIGENCIA_SUPERPUESTA", "Ya existe una política activa con vigencia superpuesta para ese alcance.");
        }
    }

    static boolean overlaps(Instant aFrom, Instant aTo, Instant bFrom, Instant bTo) {
        boolean aStartsBeforeBEnds = bTo == null || aFrom.isBefore(bTo);
        boolean bStartsBeforeAEnds = aTo == null || bFrom.isBefore(aTo);
        return aStartsBeforeBEnds && bStartsBeforeAEnds;
    }

    static SlaPolicyResponse toResponse(SlaPolicy p) {
        return new SlaPolicyResponse(Ids.str(p.id()), Ids.str(p.categoriaId()), Ids.str(p.prioridadId()), p.horasObjetivo(),
                p.horasAviso(), Times.iso(p.vigenteDesde()), Times.iso(p.vigenteHasta()), p.activo());
    }
}
