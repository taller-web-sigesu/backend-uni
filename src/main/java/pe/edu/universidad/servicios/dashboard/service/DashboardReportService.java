package pe.edu.universidad.servicios.dashboard.service;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.universidad.servicios.dashboard.domain.DashboardScope;
import pe.edu.universidad.servicios.dashboard.dto.DashboardDtos.DashboardGroupResponse;
import pe.edu.universidad.servicios.dashboard.dto.DashboardDtos.DashboardSummaryResponse;
import pe.edu.universidad.servicios.dashboard.report.DashboardCsvWriter;
import pe.edu.universidad.servicios.dashboard.repository.DashboardReadRepository;
import pe.edu.universidad.servicios.shared.error.ApiException;
import pe.edu.universidad.servicios.shared.security.AuthenticatedActor;
import pe.edu.universidad.servicios.shared.web.Times;

/**
 * Exportación CSV (HU-14) con el mismo alcance y filtros del resumen.
 */
@Service
@Transactional(readOnly = true)
public class DashboardReportService {

    public record CsvReport(String filename, String content) {
    }

    private final DashboardService dashboard;
    private final DashboardReadRepository reads;

    public DashboardReportService(DashboardService dashboard, DashboardReadRepository reads) {
        this.dashboard = dashboard;
        this.reads = reads;
    }

    public CsvReport export(AuthenticatedActor actor, String tipo, Instant desde, Instant hasta, String areaId) {
        if (tipo == null || !(tipo.equals("solicitudes") || tipo.equals("indicadores"))) {
            throw ApiException.invalidField("tipo", "tipo debe ser solicitudes o indicadores.");
        }
        DashboardScope scope = dashboard.scope(actor, desde, hasta, areaId);
        return tipo.equals("solicitudes") ? requests(scope) : indicators(scope);
    }

    private CsvReport requests(DashboardScope scope) {
        DashboardCsvWriter csv = new DashboardCsvWriter()
                .row("codigo", "categoria", "area", "estado", "prioridad", "responsable", "creadoEn");
        reads.requestsForExport(scope).forEach(r -> csv.row(r.codigo(), r.categoria(), r.area(), r.estado(), r.prioridad(),
                r.responsable(), Times.iso(r.creadoEn())));
        return new CsvReport("solicitudes.csv", csv.build());
    }

    private CsvReport indicators(DashboardScope scope) {
        DashboardSummaryResponse s = dashboard.summary(scope);
        DashboardCsvWriter csv = new DashboardCsvWriter().row("indicador", "grupo", "valor");
        csv.row("registradas", "", String.valueOf(s.registradas()));
        csv.row("pendientes", "", String.valueOf(s.pendientes()));
        csv.row("atendidas", "", String.valueOf(s.atendidas()));
        for (DashboardGroupResponse g : s.porCategoria()) {
            csv.row("porCategoria", g.id(), String.valueOf(g.total()));
        }
        for (DashboardGroupResponse g : s.porPrioridad()) {
            csv.row("porPrioridad", g.id(), String.valueOf(g.total()));
        }
        for (DashboardGroupResponse g : s.porResponsable()) {
            csv.row("porResponsable", g.id() == null ? "" : g.id(), String.valueOf(g.total()));
        }
        csv.row("promedioHorasAtencion", "", String.valueOf(s.promedioHorasAtencion()));
        csv.row("porcentajeResueltas", "", String.valueOf(s.porcentajeResueltas()));
        if (s.vencidas().disponible()) {
            csv.row("vencidas", "", String.valueOf(s.vencidas().total()));
            csv.row("porVencer", "", String.valueOf(s.vencidas().porVencer()));
        }
        return new CsvReport("indicadores.csv", csv.build());
    }
}
