package pe.edu.universidad.servicios.auth.security;

import java.util.List;

import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import pe.edu.universidad.servicios.shared.config.AppProperties;
import pe.edu.universidad.servicios.shared.error.ApiException;

/**
 * Control de origen para operaciones autenticadas con cookie (protección CSRF junto con SameSite=Lax).
 * Un navegador siempre envía Origin en un POST entre orígenes; si llega, debe estar en la lista permitida.
 */
@Component
public class OriginGuard {

    private final List<String> allowed;

    public OriginGuard(AppProperties properties) {
        this.allowed = properties.security().allowedOrigins().stream().map(String::trim).toList();
    }

    public void check(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        if (origin == null) {
            String referer = request.getHeader("Referer");
            if (referer == null) {
                return;
            }
            origin = originOf(referer);
        }
        if (!allowed.contains(origin)) {
            throw ApiException.forbidden("ORIGEN_NO_PERMITIDO", "El origen de la petición no está autorizado.");
        }
    }

    private static String originOf(String url) {
        int scheme = url.indexOf("://");
        if (scheme < 0) {
            return url;
        }
        int end = url.indexOf('/', scheme + 3);
        return end < 0 ? url : url.substring(0, end);
    }

    public List<String> allowedOrigins() {
        return allowed;
    }
}
