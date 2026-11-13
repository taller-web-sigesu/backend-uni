package pe.edu.universidad.servicios.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import pe.edu.universidad.servicios.catalogs.repository.CatalogRecords.Transition;
import pe.edu.universidad.servicios.management.domain.RequestTransitionPolicy;
import pe.edu.universidad.servicios.reservations.domain.ReservationTimePolicy;
import pe.edu.universidad.servicios.reservations.repository.ReservationRepository.TimeRange;
import pe.edu.universidad.servicios.reservations.repository.SpaceConfigRepository.ScheduleSlot;
import pe.edu.universidad.servicios.shared.error.ApiException;
import pe.edu.universidad.servicios.shared.security.RoleCode;
import pe.edu.universidad.servicios.sla.service.SlaResolutionService;

/**
 * Pruebas unitarias de políticas de dominio, sin Spring ni base de datos.
 */
class DomainPolicyTest {

    private static final List<Transition> TRANSITIONS = List.of(
            new Transition(1, "NUEVO", "ASIGNADO", "SUPERVISOR", false),
            new Transition(2, "NUEVO", "RECHAZADO", "SUPERVISOR", true),
            new Transition(3, "ASIGNADO", "EN_PROCESO", "TECNICO", false),
            new Transition(4, "ASIGNADO", "RECHAZADO", "SUPERVISOR", true),
            new Transition(5, "EN_PROCESO", "RESUELTO", "TECNICO", false),
            new Transition(6, "EN_PROCESO", "RECHAZADO", "SUPERVISOR", true),
            new Transition(7, "RESUELTO", "CERRADO", "SUPERVISOR", false));

    private static HttpStatus statusOf(Runnable action) {
        try {
            action.run();
            return HttpStatus.OK;
        } catch (ApiException ex) {
            return ex.getStatus();
        }
    }

    @Test
    void transicionesPorRolYOrigen() {
        assertThat(statusOf(() -> RequestTransitionPolicy.evaluate(TRANSITIONS, RoleCode.TECNICO, "ASIGNADO", false, "EN_PROCESO", null)))
                .isEqualTo(HttpStatus.OK);
        assertThat(statusOf(() -> RequestTransitionPolicy.evaluate(TRANSITIONS, RoleCode.SUPERVISOR, "ASIGNADO", false, "RESUELTO", null)))
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(statusOf(() -> RequestTransitionPolicy.evaluate(TRANSITIONS, RoleCode.SUPERVISOR, "NUEVO", false, "CERRADO", null)))
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(statusOf(() -> RequestTransitionPolicy.evaluate(TRANSITIONS, RoleCode.SUPERVISOR, "NUEVO", false, "RECHAZADO", " ")))
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(statusOf(() -> RequestTransitionPolicy.evaluate(TRANSITIONS, RoleCode.SUPERVISOR, "RECHAZADO", true, "RECHAZADO", "x")))
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(statusOf(() -> RequestTransitionPolicy.evaluate(TRANSITIONS, RoleCode.SUPERVISOR, "NUEVO", false, "ASIGNADO", null)))
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void estadoSlaEnLosLimites() {
        Instant limite = Instant.parse("2026-11-07T15:00:00Z");
        Instant aviso = Instant.parse("2026-11-07T11:00:00Z");
        assertThat(SlaResolutionService.status(false, limite, aviso, aviso.minusNanos(1000))).isEqualTo("EN_PLAZO");
        assertThat(SlaResolutionService.status(false, limite, aviso, aviso)).isEqualTo("POR_VENCER");
        assertThat(SlaResolutionService.status(false, limite, aviso, limite)).isEqualTo("POR_VENCER");
        assertThat(SlaResolutionService.status(false, limite, aviso, limite.plusNanos(1000))).isEqualTo("VENCIDA");
        assertThat(SlaResolutionService.status(true, limite, aviso, limite.plusSeconds(10))).isEqualTo("CERRADA");
        assertThat(SlaResolutionService.status(false, null, null, limite)).isEqualTo("SIN_SLA");
    }

    @Test
    void franjasLibresRespetanIntervalosSemiabiertos() {
        ScheduleSlot day = new ScheduleSlot(2, LocalTime.of(8, 0), LocalTime.of(18, 0));
        List<TimeRange> free = ReservationTimePolicy.freeRanges(day, List.of(
                new TimeRange(LocalTime.of(10, 0), LocalTime.of(11, 0)),
                new TimeRange(LocalTime.of(11, 0), LocalTime.of(12, 0))));
        assertThat(free).containsExactly(
                new TimeRange(LocalTime.of(8, 0), LocalTime.of(10, 0)),
                new TimeRange(LocalTime.of(12, 0), LocalTime.of(18, 0)));
    }

    @Test
    void fechaYHoraDeReservaSeInterpretanEnLima() {
        assertThat(ReservationTimePolicy.toInstant(LocalDate.of(2026, 11, 10), LocalTime.of(10, 0)))
                .isEqualTo(Instant.parse("2026-11-10T15:00:00Z"));
        assertThat(ReservationTimePolicy.today(Instant.parse("2026-11-11T03:00:00Z"))).isEqualTo(LocalDate.of(2026, 11, 10));
        assertThat(ReservationTimePolicy.operationalState("CONFIRMADA", LocalDate.of(2026, 11, 10), LocalTime.of(10, 0),
                LocalTime.of(11, 0), Instant.parse("2026-11-10T16:00:00Z"))).isEqualTo("FINALIZADA");
        assertThatThrownBy(() -> pe.edu.universidad.servicios.shared.dto.PageQuery.of(0, 101)).isInstanceOf(ApiException.class);
    }
}
