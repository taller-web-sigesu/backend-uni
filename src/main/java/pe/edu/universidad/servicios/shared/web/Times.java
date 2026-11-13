package pe.edu.universidad.servicios.shared.web;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * Instantes en UTC; fechas y horas de reserva en el calendario America/Lima.
 */
public final class Times {

    public static final ZoneId CAMPUS_ZONE = ZoneId.of("America/Lima");
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private Times() {
    }

    public static String hhmm(LocalTime time) {
        return time == null ? null : time.format(HH_MM);
    }

    public static String iso(Instant instant) {
        return instant == null ? null : instant.truncatedTo(ChronoUnit.SECONDS).toString();
    }
}
