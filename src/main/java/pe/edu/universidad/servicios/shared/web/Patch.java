package pe.edu.universidad.servicios.shared.web;

import pe.edu.universidad.servicios.shared.error.ApiException;

/**
 * Ayudas para PATCH parciales: un campo ausente (null) conserva el valor actual.
 */
public final class Patch {

    private Patch() {
    }

    public static <T> T or(T value, T current) {
        return value != null ? value : current;
    }

    public static String text(String field, String value, String current) {
        if (value == null) {
            return current;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw ApiException.invalidField(field, field + " no puede estar vacío.");
        }
        return trimmed;
    }

    /** Texto opcional: cadena vacía lo borra. */
    public static String optionalText(String value, String current) {
        if (value == null) {
            return current;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static void rejectCodeChange(String codigo) {
        if (codigo != null) {
            throw ApiException.invalidField("codigo", "El código es estable y no se puede modificar.");
        }
    }

    public static void requireAny(Object... values) {
        for (Object value : values) {
            if (value != null) {
                return;
            }
        }
        throw ApiException.badRequest("DATOS_INVALIDOS", "Envíe al menos un campo para actualizar.");
    }

    public static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
