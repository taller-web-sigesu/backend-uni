package pe.edu.universidad.servicios.management.domain;

import java.util.List;
import java.util.Optional;

import pe.edu.universidad.servicios.catalogs.repository.CatalogRecords.Transition;
import pe.edu.universidad.servicios.shared.error.ApiException;
import pe.edu.universidad.servicios.shared.security.RoleCode;

/**
 * Decide si un rol puede llevar una solicitud de un estado a otro según {@code transiciones_estado}.
 * Un rol sin ninguna transición hacia el destino recibe 403; un origen no permitido recibe 409.
 */
public final class RequestTransitionPolicy {

    public static final String ASIGNADO = "ASIGNADO";

    private RequestTransitionPolicy() {
    }

    public record Decision(boolean requiresReason) {
    }

    public static Decision evaluate(List<Transition> transitions, RoleCode role, String from, boolean fromIsFinal,
            String to, String motivo) {
        if (ASIGNADO.equals(to)) {
            throw ApiException.badRequest("USAR_ASIGNACION", "Para asignar un técnico use POST /requests/{id}/assignments.");
        }
        List<Transition> forRoleAndTarget = transitions.stream()
                .filter(t -> t.rol().equals(role.name()) && t.destino().equals(to))
                .toList();
        if (forRoleAndTarget.isEmpty()) {
            throw ApiException.forbidden("TRANSICION_NO_PERMITIDA_ROL", "El rol no puede llevar la solicitud a " + to + ".");
        }
        boolean requiresReason = forRoleAndTarget.stream().anyMatch(Transition::requiereMotivo);
        if (requiresReason && (motivo == null || motivo.isBlank())) {
            throw ApiException.invalidField("motivo", "El motivo es obligatorio para pasar a " + to + ".");
        }
        Optional<Transition> match = fromIsFinal ? Optional.empty()
                : forRoleAndTarget.stream().filter(t -> t.origen().equals(from)).findFirst();
        if (match.isEmpty()) {
            throw ApiException.conflict("TRANSICION_INVALIDA", "No se puede pasar de " + from + " a " + to + ".");
        }
        return new Decision(match.get().requiereMotivo());
    }
}
