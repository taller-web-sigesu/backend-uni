package pe.edu.universidad.servicios.shared.telemetry;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class BusinessMetrics {

    private final Counter requestsCreated;
    private final Counter reservationsCreated;
    private final Counter invalidLogins;
    private final Counter blockedLogins;

    public BusinessMetrics(MeterRegistry meters) {
        requestsCreated = meters.counter("gestal.business.operations", "operation", "request_created");
        reservationsCreated = meters.counter("gestal.business.operations", "operation", "reservation_created");
        invalidLogins = meters.counter("gestal.auth.login.rejections", "reason", "invalid_credentials");
        blockedLogins = meters.counter("gestal.auth.login.rejections", "reason", "rate_limited");
    }

    public void requestCreated() {
        incrementAfterCommit(requestsCreated);
    }

    public void reservationCreated() {
        incrementAfterCommit(reservationsCreated);
    }

    public void invalidLogin() {
        invalidLogins.increment();
    }

    public void blockedLogin() {
        blockedLogins.increment();
    }

    private void incrementAfterCommit(Counter counter) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                counter.increment();
            }
        });
    }
}
