package pe.edu.universidad.servicios.shared.dto;

import pe.edu.universidad.servicios.shared.error.ApiException;

/**
 * Paginación desde cero, 20 elementos por defecto y 100 como máximo.
 */
public record PageQuery(int page, int size) {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    public static PageQuery of(Integer page, Integer size) {
        int p = page == null ? 0 : page;
        int s = size == null ? DEFAULT_SIZE : size;
        if (p < 0) {
            throw ApiException.invalidField("page", "page debe ser mayor o igual a 0.");
        }
        if (s < 1 || s > MAX_SIZE) {
            throw ApiException.invalidField("size", "size debe estar entre 1 y " + MAX_SIZE + ".");
        }
        return new PageQuery(p, s);
    }

    public long offset() {
        return (long) page * size;
    }
}
