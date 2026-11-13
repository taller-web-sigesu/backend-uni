-- Datos ficticios para local y CI. Se cargan solo si se activa explícitamente:
--   APP_FLYWAY_LOCATIONS=classpath:db/migration,classpath:db/demo
-- Contraseña de todas las cuentas: Demo2026!  (hash BCrypt, costo 10). No usar en despliegues reales.

INSERT INTO usuarios (rol_id, area_id, nombre_completo, correo, hash_contrasena, activo, estado_cuenta,
                      origen_registro, debe_cambiar_contrasena)
SELECT r.id, x.area_id, x.nombre, x.correo,
       '$2a$10$eqF73EMeTiOez7mrvt7FduOBCAQg.QmaTnZyfdTgrEFY7ekrZWPk.', 1, 'ACTIVA', 'ADMINISTRADOR', 0
FROM (
    SELECT 'ADMINISTRADOR' AS rol, NULL AS area_id, 'Admin Demo' AS nombre, 'admin.demo@universidad.example' AS correo
    UNION ALL SELECT 'SUPERVISOR', 1, 'Sofía Supervisora TI', 'supervisor.ti.demo@universidad.example'
    UNION ALL SELECT 'TECNICO', 1, 'Luis Técnico TI', 'tecnico.ti.demo@universidad.example'
    UNION ALL SELECT 'TECNICO', 1, 'Carla Técnica TI', 'tecnica.ti.demo@universidad.example'
    UNION ALL SELECT 'SUPERVISOR', 2, 'Mario Supervisor Mantenimiento', 'supervisor.mant.demo@universidad.example'
    UNION ALL SELECT 'TECNICO', 2, 'Rosa Técnica Mantenimiento', 'tecnico.mant.demo@universidad.example'
    UNION ALL SELECT 'SUPERVISOR', 4, 'Elena Supervisora Biblioteca', 'supervisor.bib.demo@universidad.example'
    UNION ALL SELECT 'ESTUDIANTE', NULL, 'Ana Estudiante', 'ana.demo@universidad.example'
    UNION ALL SELECT 'DOCENTE', NULL, 'Pedro Docente', 'pedro.demo@universidad.example'
) x
JOIN roles r ON r.codigo = x.rol;
