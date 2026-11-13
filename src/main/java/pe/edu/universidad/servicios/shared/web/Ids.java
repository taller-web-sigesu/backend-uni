package pe.edu.universidad.servicios.shared.web;

import pe.edu.universidad.servicios.shared.error.ApiException;

/**
 * Los identificadores se serializan como cadenas decimales.
 */
public final class Ids {

    private Ids() {
    }

    public static String str(Long id) {
        return id == null ? null : Long.toString(id);
    }

    public static Long parse(String field, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            long id = Long.parseLong(value.trim());
            if (id <= 0) {
                throw new NumberFormatException();
            }
            return id;
        } catch (NumberFormatException ex) {
            throw ApiException.invalidField(field, field + " debe ser un identificador numérico válido.");
        }
    }

    public static Long required(String field, String value) {
        Long id = parse(field, value);
        if (id == null) {
            throw ApiException.invalidField(field, field + " es obligatorio.");
        }
        return id;
    }
}
