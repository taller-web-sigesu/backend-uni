package pe.edu.universidad.servicios.sla.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Service;

import pe.edu.universidad.servicios.sla.repository.SlaPolicyRepository;
import pe.edu.universidad.servicios.sla.repository.SlaPolicyRepository.SlaPolicy;

/**
 * Resuelve la política aplicable al registrar: primero la categoría y, si no hay, la prioridad.
 */
@Service
public class SlaResolutionService {

    private final SlaPolicyRepository policies;

    public SlaResolutionService(SlaPolicyRepository policies) {
        this.policies = policies;
    }

    public record SlaDeadline(long policyId, Instant fechaLimite, Instant fechaAviso) {
    }

    public Optional<SlaDeadline> resolve(long categoriaId, long prioridadId, Instant registeredAt) {
        Optional<SlaPolicy> policy = policies.effectiveForCategory(categoriaId, registeredAt)
                .or(() -> policies.effectiveForPriority(prioridadId, registeredAt));
        return policy.map(p -> {
            Instant limite = registeredAt.plus(Duration.ofHours(p.horasObjetivo()));
            return new SlaDeadline(p.id(), limite, limite.minus(Duration.ofHours(p.horasAviso())));
        });
    }

    /** El indicador de vencimientos se considera operativo si hay alguna política activa. */
    public boolean isOperational() {
        return policies.anyActive();
    }

    /** Estado SLA derivado del reloj; equivalente a la vista vw_estado_sla. */
    public static String status(boolean esFinal, Instant fechaLimite, Instant fechaAviso, Instant now) {
        if (esFinal) {
            return "CERRADA";
        }
        if (fechaLimite == null) {
            return "SIN_SLA";
        }
        if (now.isAfter(fechaLimite)) {
            return "VENCIDA";
        }
        if (fechaAviso != null && !now.isBefore(fechaAviso)) {
            return "POR_VENCER";
        }
        return "EN_PLAZO";
    }
}
