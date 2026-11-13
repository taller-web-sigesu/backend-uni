package pe.edu.universidad.servicios.shared.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Contador de eventos por clave en una ventana deslizante, en memoria de la instancia.
 */
public class SlidingWindowLimiter {

    private final Map<String, Deque<Instant>> events = new ConcurrentHashMap<>();
    private final Clock clock;
    private final int maxEvents;
    private final Duration window;

    public SlidingWindowLimiter(int maxEvents, Duration window, Clock clock) {
        this.clock = clock;
        this.maxEvents = maxEvents;
        this.window = window;
    }

    public boolean isBlocked(String key) {
        Deque<Instant> recorded = events.get(key);
        if (recorded == null) {
            return false;
        }
        synchronized (recorded) {
            prune(recorded);
            return recorded.size() >= maxEvents;
        }
    }

    public void record(String key) {
        Deque<Instant> recorded = events.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (recorded) {
            prune(recorded);
            recorded.addLast(clock.instant());
        }
        if (events.size() > 10_000) {
            events.entrySet().removeIf(e -> {
                synchronized (e.getValue()) {
                    prune(e.getValue());
                    return e.getValue().isEmpty();
                }
            });
        }
    }

    public void reset(String key) {
        events.remove(key);
    }

    private void prune(Deque<Instant> recorded) {
        Instant limit = clock.instant().minus(window);
        while (!recorded.isEmpty() && recorded.peekFirst().isBefore(limit)) {
            recorded.pollFirst();
        }
    }
}
