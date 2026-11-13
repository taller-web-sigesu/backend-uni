package pe.edu.universidad.servicios.reservations.domain;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import pe.edu.universidad.servicios.reservations.repository.ReservationRepository.TimeRange;
import pe.edu.universidad.servicios.reservations.repository.SpaceConfigRepository.ReservationPolicy;
import pe.edu.universidad.servicios.reservations.repository.SpaceConfigRepository.ScheduleSlot;
import pe.edu.universidad.servicios.shared.error.ApiException;
import pe.edu.universidad.servicios.shared.web.Times;

/**
 * Reglas de franja: calendario America/Lima, intervalos [inicio, fin) y política global.
 */
public final class ReservationTimePolicy {

    private ReservationTimePolicy() {
    }

    public static Instant toInstant(LocalDate fecha, LocalTime hora) {
        return fecha.atTime(hora).atZone(Times.CAMPUS_ZONE).toInstant();
    }

    public static LocalDate today(Instant now) {
        return now.atZone(Times.CAMPUS_ZONE).toLocalDate();
    }

    /** Valida una nueva franja contra reloj, política y horario del día (errores 400). */
    public static void validateNewSlot(LocalDate fecha, LocalTime inicio, LocalTime fin, Instant now,
            ReservationPolicy policy, ScheduleSlot schedule) {
        if (!inicio.isBefore(fin)) {
            throw ApiException.badRequest("FRANJA_INVALIDA", "horaInicio debe ser anterior a horaFin.");
        }
        Instant start = toInstant(fecha, inicio);
        if (!start.isAfter(now)) {
            throw ApiException.badRequest("FRANJA_PASADA", "La franja ya comenzó o está en el pasado.");
        }
        if (start.isBefore(now.plus(Duration.ofHours(policy.horasAnticipacionMinima())))) {
            throw ApiException.badRequest("ANTICIPACION_INSUFICIENTE",
                    "La reserva requiere al menos " + policy.horasAnticipacionMinima() + " hora(s) de anticipación.");
        }
        if (fecha.isAfter(today(now).plusDays(policy.diasAnticipacionMaxima()))) {
            throw ApiException.badRequest("ANTICIPACION_EXCEDIDA",
                    "Solo se puede reservar hasta " + policy.diasAnticipacionMaxima() + " días calendario por adelantado.");
        }
        long minutes = Duration.between(inicio, fin).toMinutes();
        if (minutes < policy.duracionMinimaMinutos() || minutes > policy.duracionMaximaMinutos()) {
            throw ApiException.badRequest("DURACION_INVALIDA", "La duración debe estar entre "
                    + policy.duracionMinimaMinutos() + " y " + policy.duracionMaximaMinutos() + " minutos.");
        }
        if (schedule == null) {
            throw ApiException.badRequest("ESPACIO_CERRADO", "El espacio no atiende ese día.");
        }
        if (inicio.isBefore(schedule.horaApertura()) || fin.isAfter(schedule.horaCierre())) {
            throw ApiException.badRequest("FUERA_DE_HORARIO", "La franja está fuera del horario del espacio ("
                    + Times.hhmm(schedule.horaApertura()) + "-" + Times.hhmm(schedule.horaCierre()) + ").");
        }
    }

    /** Estado operativo derivado del reloj para reservas confirmadas. */
    public static String operationalState(String estado, LocalDate fecha, LocalTime inicio, LocalTime fin, Instant now) {
        if (!"CONFIRMADA".equals(estado)) {
            return estado;
        }
        if (!now.isBefore(toInstant(fecha, fin))) {
            return "FINALIZADA";
        }
        if (!now.isBefore(toInstant(fecha, inicio))) {
            return "EN_CURSO";
        }
        return "PROXIMA";
    }

    /** Huecos libres del horario descontando las franjas ocupadas. */
    public static List<TimeRange> freeRanges(ScheduleSlot schedule, List<TimeRange> occupied) {
        List<TimeRange> free = new ArrayList<>();
        LocalTime cursor = schedule.horaApertura();
        for (TimeRange busy : occupied) {
            if (busy.inicio().isAfter(cursor)) {
                LocalTime end = busy.inicio().isBefore(schedule.horaCierre()) ? busy.inicio() : schedule.horaCierre();
                if (end.isAfter(cursor)) {
                    free.add(new TimeRange(cursor, end));
                }
            }
            if (busy.fin().isAfter(cursor)) {
                cursor = busy.fin();
            }
        }
        if (cursor.isBefore(schedule.horaCierre())) {
            free.add(new TimeRange(cursor, schedule.horaCierre()));
        }
        return free;
    }
}
