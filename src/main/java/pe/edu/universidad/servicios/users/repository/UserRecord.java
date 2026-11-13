package pe.edu.universidad.servicios.users.repository;

import java.time.Instant;

import pe.edu.universidad.servicios.shared.security.RoleCode;

public record UserRecord(
        long id,
        RoleCode role,
        Long areaId,
        String nombreCompleto,
        String correo,
        String hashContrasena,
        String telefono,
        boolean activo,
        String estadoCuenta,
        boolean debeCambiarContrasena,
        String origenRegistro,
        Instant creadoEn) {

    public boolean canLogin() {
        return activo && "ACTIVA".equals(estadoCuenta);
    }
}
