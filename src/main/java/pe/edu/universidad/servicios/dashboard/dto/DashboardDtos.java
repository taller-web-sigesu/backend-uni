package pe.edu.universidad.servicios.dashboard.dto;

import java.util.List;

public final class DashboardDtos {

    private DashboardDtos() {
    }

    public record DashboardFiltersResponse(String fechaDesde, String fechaHasta, String areaId) {
    }

    public record DashboardGroupResponse(String id, String nombre, long total) {
    }

    public record DashboardSlaResponse(long total, long porVencer, boolean disponible) {
    }

    public record DashboardSummaryResponse(
            DashboardFiltersResponse filtros,
            long registradas,
            long pendientes,
            long atendidas,
            List<DashboardGroupResponse> porCategoria,
            List<DashboardGroupResponse> porPrioridad,
            List<DashboardGroupResponse> porResponsable,
            double promedioHorasAtencion,
            double porcentajeResueltas,
            DashboardSlaResponse vencidas) {
    }
}
