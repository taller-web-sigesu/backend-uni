package pe.edu.universidad.servicios.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * Reloj controlable para pruebas; por defecto sigue al reloj real con un desplazamiento opcional.
 */
public class MutableClock extends Clock {

    private volatile Instant fixed;
    private volatile Duration offset = Duration.ZERO;

    public void reset() {
        fixed = null;
        offset = Duration.ZERO;
    }

    public void fixAt(Instant instant) {
        fixed = instant;
    }

    public void advance(Duration duration) {
        if (fixed != null) {
            fixed = fixed.plus(duration);
        } else {
            offset = offset.plus(duration);
        }
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        Instant base = fixed != null ? fixed : Instant.now();
        return fixed != null ? base : base.plus(offset);
    }
}
