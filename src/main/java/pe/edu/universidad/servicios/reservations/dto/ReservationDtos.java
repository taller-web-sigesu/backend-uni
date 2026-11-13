package pe.edu.universidad.servicios.reservations.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Esquemas HTTP de reservas de ambientes (HU-21 a HU-25). Fechas y horas en calendario America/Lima.
 */
public final class ReservationDtos {

    private ReservationDtos() {
    }

    static final String CODE_PATTERN = "[A-Z0-9_\\-]+";

    // Consulta de espacios

    public record IdName(String id, String nombre) {
    }

    public record DayScheduleResponse(String horaApertura, String horaCierre) {
    }

    public record SpaceSummaryResponse(String id, String codigo, String nombre, IdName tipo, String areaId, String pabellon,
            String piso, int capacidad, DayScheduleResponse horario) {
    }

    public record TimeSlotResponse(String horaInicio, String horaFin) {
    }

    public record SpaceAvailabilityResponse(String espacioId, String fecha, DayScheduleResponse horario,
            List<TimeSlotResponse> libres, List<TimeSlotResponse> ocupadas) {
    }

    // Reservas

    public record CreateReservationRequest(
            @NotBlank(message = "espacioId es obligatorio.") String espacioId,
            @NotNull(message = "fecha es obligatoria.") LocalDate fecha,
            @NotNull(message = "horaInicio es obligatoria.") LocalTime horaInicio,
            @NotNull(message = "horaFin es obligatoria.") LocalTime horaFin,
            @NotBlank(message = "motivo es obligatorio.") @Size(max = 255, message = "motivo admite hasta 255 caracteres.") String motivo,
            @Positive(message = "numAsistentes debe ser positivo.") @Max(value = 65535, message = "numAsistentes es demasiado grande.") Integer numAsistentes) {
    }

    public record ReservationSummaryResponse(String id, String codigo, String espacioId, String espacio, String fecha,
            String horaInicio, String horaFin, String estado, String estadoOperativo) {
    }

    public record ReservationDetailResponse(String id, String codigo, String espacioId, String espacio, String fecha,
            String horaInicio, String horaFin, String estado, String estadoOperativo, String solicitanteId, String motivo,
            Integer numAsistentes, String motivoRechazo, String creadoEn) {
    }

    public record ReservationHistoryResponse(String estadoAnterior, String estado, String actorId, String fecha, String motivo) {
    }

    public record ChangeReservationStatusRequest(
            @NotBlank(message = "estado es obligatorio.") String estado,
            @Size(max = 2000, message = "motivo admite hasta 2000 caracteres.") String motivo) {
    }

    // Administración de espacios

    public record CreateSpaceRequest(
            @NotBlank(message = "codigo es obligatorio.") @Size(max = 40) @Pattern(regexp = CODE_PATTERN, message = "codigo admite mayúsculas, dígitos, guion y guion bajo.") String codigo,
            @NotBlank(message = "nombre es obligatorio.") @Size(max = 120) String nombre,
            @NotBlank(message = "tipoEspacioId es obligatorio.") String tipoEspacioId,
            @NotNull(message = "capacidad es obligatoria.") @Min(value = 1, message = "capacidad debe ser positiva.") @Max(value = 65535, message = "capacidad es demasiado grande.") Integer capacidad,
            String areaId,
            @Size(max = 60) String pabellon,
            @Size(max = 20) String piso,
            @Size(max = 255) String descripcion,
            @Size(max = 255) String ubicacionDetallada,
            Boolean requiereAprobacion) {
    }

    public record UpdateSpaceRequest(
            @Size(min = 1, max = 120) String nombre,
            String tipoEspacioId,
            String areaId,
            @Size(max = 60) String pabellon,
            @Size(max = 20) String piso,
            @Size(max = 255) String descripcion,
            @Size(max = 255) String ubicacionDetallada,
            @Min(value = 1, message = "capacidad debe ser positiva.") @Max(value = 65535, message = "capacidad es demasiado grande.") Integer capacidad,
            Boolean activo,
            Boolean requiereAprobacion,
            String codigo) {
    }

    public record SpaceResponse(String id, String codigo, String nombre, String tipoEspacioId, String areaId, String pabellon,
            String piso, String descripcion, String ubicacionDetallada, int capacidad, boolean requiereAprobacion,
            boolean activo) {
    }

    public record CreateSpaceTypeRequest(
            @NotBlank(message = "codigo es obligatorio.") @Size(max = 40) @Pattern(regexp = CODE_PATTERN, message = "codigo admite mayúsculas, dígitos, guion y guion bajo.") String codigo,
            @NotBlank(message = "nombre es obligatorio.") @Size(max = 80) String nombre,
            @Size(max = 255) String descripcion) {
    }

    public record UpdateSpaceTypeRequest(@Size(min = 1, max = 80) String nombre, @Size(max = 255) String descripcion,
            Boolean activo, String codigo) {
    }

    public record SpaceTypeResponse(String id, String codigo, String nombre, String descripcion, boolean activo) {
    }

    public record ScheduleSlotRequest(
            @NotNull(message = "diaSemana es obligatorio.") @Min(value = 1, message = "diaSemana va de 1 a 7.") @Max(value = 7, message = "diaSemana va de 1 a 7.") Integer diaSemana,
            @NotNull(message = "horaApertura es obligatoria.") LocalTime horaApertura,
            @NotNull(message = "horaCierre es obligatoria.") LocalTime horaCierre) {
    }

    public record ReplaceSpaceScheduleRequest(@NotNull(message = "franjas es obligatorio.") @Valid List<ScheduleSlotRequest> franjas) {
    }

    public record ScheduleSlotResponse(int diaSemana, String horaApertura, String horaCierre) {
    }

    public record SpaceScheduleResponse(List<ScheduleSlotResponse> franjas) {
    }

    public record ReservationPolicyRequest(
            @NotNull(message = "duracionMinimaMinutos es obligatorio.") @Min(value = 1, message = "duracionMinimaMinutos debe ser positiva.") @Max(65535) Integer duracionMinimaMinutos,
            @NotNull(message = "duracionMaximaMinutos es obligatorio.") @Min(value = 1, message = "duracionMaximaMinutos debe ser positiva.") @Max(65535) Integer duracionMaximaMinutos,
            @NotNull(message = "horasAnticipacionMinima es obligatorio.") @Min(value = 0, message = "horasAnticipacionMinima no puede ser negativa.") @Max(65535) Integer horasAnticipacionMinima,
            @NotNull(message = "diasAnticipacionMaxima es obligatorio.") @Min(value = 0, message = "diasAnticipacionMaxima no puede ser negativa.") @Max(65535) Integer diasAnticipacionMaxima,
            @NotNull(message = "maxReservasActivasPorDia es obligatorio.") @Min(value = 1, message = "maxReservasActivasPorDia debe ser positivo.") @Max(value = 255, message = "maxReservasActivasPorDia admite hasta 255.") Integer maxReservasActivasPorDia) {
    }

    public record ReservationPolicyResponse(int duracionMinimaMinutos, int duracionMaximaMinutos, int horasAnticipacionMinima,
            int diasAnticipacionMaxima, int maxReservasActivasPorDia) {
    }
}
