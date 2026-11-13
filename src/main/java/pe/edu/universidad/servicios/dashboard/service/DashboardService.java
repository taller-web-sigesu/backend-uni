package pe.edu.universidad.servicios.dashboard.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.universidad.servicios.catalogs.service.AreaCatalogService;
import pe.edu.universidad.servicios.dashboard.domain.DashboardScope;
import pe.edu.universidad.servicios.dashboard.dto.DashboardDtos.DashboardFiltersResponse;
import pe.edu.universidad.servicios.dashboard.dto.DashboardDtos.DashboardGroupResponse;
import pe.edu.universidad.servicios.dashboard.dto.DashboardDtos.DashboardSlaResponse;
import pe.edu.universidad.servicios.dashboard.dto.DashboardDtos.DashboardSummaryResponse;
import pe.edu.universidad.servicios.dashboard.repository.DashboardReadRepository;
import pe.edu.universidad.servicios.dashboard.repository.DashboardReadRepository.Group;
import pe.edu.universidad.servicios.dashboard.repository.DashboardReadRepository.Totals;
import pe.edu.universidad.servicios.shared.error.ApiException;
import pe.edu.universidad.servicios.shared.security.AuthenticatedActor;
import pe.edu.universidad.servicios.shared.web.Ids;
import pe.edu.universidad.servicios.shared.web.Times;
import pe.edu.universidad.servicios.sla.service.SlaResolutionService;

/**
 * Indicadores de atención (HU-12 y HU-13). Resumen y CSV comparten alcance y definiciones.
 */
@Service
@Transactional(readOnly = true)
public class DashboardService {

    private final DashboardReadRepository reads;
    private final AreaCatalogService areas;
    private final SlaResolutionService sla;
    private final Clock clock;

    public DashboardService(DashboardReadRepository reads, AreaCatalogService areas, SlaResolutionService sla, Clock clock) {
        this.reads = reads;
        this.areas = areas;
        this.sla = sla;
        this.clock = clock;
    }

    public DashboardScope scope(AuthenticatedActor actor, Instant desde, Instant hasta, String areaId) {
        Long requestedArea = Ids.parse("areaId", areaId);
        DashboardScope scope = DashboardScope.resolve(actor, desde, hasta, requestedArea);
        if (scope.areaId() != null && areas.find(scope.areaId()).isEmpty()) {
            throw ApiException.invalidField("areaId", "El área no existe.");
        }
        return scope;
    }

    public DashboardSummaryResponse summary(AuthenticatedActor actor, Instant desde, Instant hasta, String areaId) {
        DashboardScope scope = scope(actor, desde, hasta, areaId);
        return summary(scope);
    }

    public DashboardSummaryResponse summary(DashboardScope scope) {
        Totals totals = reads.totals(scope, clock.instant());
        boolean slaOperational = sla.isOperational();
        return new DashboardSummaryResponse(
                new DashboardFiltersResponse(Times.iso(scope.desde()), Times.iso(scope.hasta()), Ids.str(scope.areaId())),
                totals.registradas(), totals.pendientes(), totals.atendidas(),
                groups(reads.byCategory(scope)), groups(reads.byPriority(scope)), groups(reads.byResponsible(scope)),
                totals.promedioHorasAtencion(), totals.porcentajeResueltas(),
                new DashboardSlaResponse(slaOperational ? totals.vencidas() : 0, slaOperational ? totals.porVencer() : 0,
                        slaOperational));
    }

    private static List<DashboardGroupResponse> groups(List<Group> groups) {
        return groups.stream().map(g -> new DashboardGroupResponse(Ids.str(g.id()), g.nombre(), g.total())).toList();
    }
}
