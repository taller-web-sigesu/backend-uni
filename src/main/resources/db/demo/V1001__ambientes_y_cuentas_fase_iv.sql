-- Datos ficticios de Fase IV para entornos con db/demo habilitado.
-- Migración idempotente: si los ambientes o las cuentas ya existen, no se duplican.
INSERT INTO espacios
    (tipo_espacio_id, area_id, codigo, nombre, descripcion, pabellon, capacidad)
SELECT t.id, a.id, demo.codigo, demo.nombre, demo.descripcion, demo.pabellon, demo.capacidad
FROM (
    SELECT 'LABORATORIO' AS tipo, 'SOPORTE_INFORMATICO' AS area, 'LAB-10' AS codigo,
           'Laboratorio 10' AS nombre, 'Laboratorio de cómputo.' AS descripcion,
           'Pabellón nuevo' AS pabellon, 30 AS capacidad
    UNION ALL
    SELECT 'OTRO', 'SERVICIOS_GENERALES', 'CANCHA-01', 'Cancha deportiva',
           'Cancha para actividades deportivas.', 'Zona deportiva', 40
) demo
JOIN tipos_espacio t ON t.codigo = demo.tipo
JOIN areas a ON a.codigo = demo.area
LEFT JOIN espacios e ON e.codigo = demo.codigo
WHERE e.id IS NULL;

INSERT INTO horarios_espacio (espacio_id, dia_semana, hora_apertura, hora_cierre)
SELECT e.id, d.dia, '07:00:00', '22:00:00'
FROM espacios e
CROSS JOIN (
    SELECT 1 AS dia UNION ALL SELECT 2 UNION ALL SELECT 3
    UNION ALL SELECT 4 UNION ALL SELECT 5
) d
LEFT JOIN horarios_espacio h ON h.espacio_id = e.id AND h.dia_semana = d.dia
WHERE e.codigo IN ('LAB-10', 'CANCHA-01')
  AND h.espacio_id IS NULL;

-- Reutiliza el hash de la contraseña de demostración definida en V1000.
INSERT INTO usuarios (rol_id, area_id, nombre_completo, correo, hash_contrasena, activo, estado_cuenta,
                      origen_registro, debe_cambiar_contrasena)
SELECT r.id, a.id, demo.nombre, demo.correo,
       '$2a$10$eqF73EMeTiOez7mrvt7FduOBCAQg.QmaTnZyfdTgrEFY7ekrZWPk.', 1, 'ACTIVA', 'ADMINISTRADOR', 0
FROM (
    SELECT 'SUPERVISOR' AS rol, 'Supervisor Demo Servicios Generales' AS nombre,
           'supervisor.sg.demo@universidad.example' AS correo
    UNION ALL SELECT 'TECNICO', 'Técnico Demo Servicios Generales', 'tecnico.sg.demo@universidad.example'
) demo
JOIN roles r ON r.codigo = demo.rol
JOIN areas a ON a.codigo = 'SERVICIOS_GENERALES'
LEFT JOIN usuarios u ON u.correo = demo.correo
WHERE u.id IS NULL;
