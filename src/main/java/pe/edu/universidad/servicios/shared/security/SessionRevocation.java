package pe.edu.universidad.servicios.shared.security;

/**
 * Contrato compartido para revocar sesiones al desactivar cuentas o cambiar credenciales.
 */
public interface SessionRevocation {

    void revokeAllForUser(long userId);
}
