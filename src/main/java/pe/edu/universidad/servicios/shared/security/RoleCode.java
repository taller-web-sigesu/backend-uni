package pe.edu.universidad.servicios.shared.security;

import java.util.Arrays;
import java.util.Optional;

public enum RoleCode {
    ESTUDIANTE,
    DOCENTE,
    TECNICO,
    SUPERVISOR,
    ADMINISTRADOR;

    public boolean requiresArea() {
        return this == TECNICO || this == SUPERVISOR;
    }

    public boolean isRequester() {
        return this == ESTUDIANTE || this == DOCENTE;
    }

    public static Optional<RoleCode> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        return Arrays.stream(values()).filter(r -> r.name().equals(value.trim())).findFirst();
    }
}
