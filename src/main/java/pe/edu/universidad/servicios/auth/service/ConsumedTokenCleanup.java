package pe.edu.universidad.servicios.auth.service;

import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Elimina cada hora los hashes de refresh consumidos cuya sesión ya alcanzó su vencimiento absoluto.
 */
@Component
@EnableScheduling
public class ConsumedTokenCleanup {

    private final SessionService sessions;

    public ConsumedTokenCleanup(SessionService sessions) {
        this.sessions = sessions;
    }

    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT10M")
    public void purge() {
        sessions.purgeConsumed();
    }
}
