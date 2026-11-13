package pe.edu.universidad.servicios.dashboard.controller;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import pe.edu.universidad.servicios.dashboard.dto.DashboardDtos.DashboardSummaryResponse;
import pe.edu.universidad.servicios.dashboard.service.DashboardReportService;
import pe.edu.universidad.servicios.dashboard.service.DashboardReportService.CsvReport;
import pe.edu.universidad.servicios.dashboard.service.DashboardService;
import pe.edu.universidad.servicios.shared.security.AuthenticatedActor;
import pe.edu.universidad.servicios.shared.security.CurrentActor;

@RestController
@RequestMapping("/api/v1/dashboard")
@Tag(name = "Dashboard y reportes", description = "HU-12 a HU-14")
public class DashboardController {

    private static final MediaType TEXT_CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);

    private final DashboardService dashboard;
    private final DashboardReportService reports;

    public DashboardController(DashboardService dashboard, DashboardReportService reports) {
        this.dashboard = dashboard;
        this.reports = reports;
    }

    @GetMapping("/summary")
    @Operation(summary = "Resume la atención de solicitudes para el periodo [fechaDesde, fechaHasta)")
    public DashboardSummaryResponse summary(@CurrentActor AuthenticatedActor actor, @RequestParam Instant fechaDesde,
            @RequestParam Instant fechaHasta, @RequestParam(required = false) String areaId) {
        return dashboard.summary(actor, fechaDesde, fechaHasta, areaId);
    }

    @GetMapping("/reports")
    @Operation(summary = "Exporta solicitudes o indicadores en CSV")
    public ResponseEntity<byte[]> report(@CurrentActor AuthenticatedActor actor, @RequestParam String tipo,
            @RequestParam Instant fechaDesde, @RequestParam Instant fechaHasta,
            @RequestParam(required = false) String areaId) {
        CsvReport report = reports.export(actor, tipo, fechaDesde, fechaHasta, areaId);
        return ResponseEntity.ok()
                .contentType(TEXT_CSV)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(report.filename()).build().toString())
                .body(report.content().getBytes(StandardCharsets.UTF_8));
    }
}
