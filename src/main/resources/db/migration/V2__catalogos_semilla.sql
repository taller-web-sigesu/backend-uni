-- Catálogos base de Gestal. Fuente: infra-uni/DISENIO-BD.md, Anexo A.
-- Los espacios de demostración y sus horarios forman parte del piloto (HU-21 a HU-25).

INSERT INTO roles (id, codigo, nombre, descripcion) VALUES
    (1, 'ESTUDIANTE', 'Estudiante', 'Registra y consulta sus propias solicitudes.'),
    (2, 'TECNICO', 'Técnico', 'Atiende las solicitudes asignadas a su área.'),
    (3, 'SUPERVISOR', 'Supervisor', 'Gestiona, asigna y cierra solicitudes de su área.'),
    (4, 'ADMINISTRADOR', 'Administrador', 'Administra usuarios y catálogos del sistema.'),
    (5, 'DOCENTE', 'Docente', 'Registra y consulta sus propias solicitudes y reservas; cuenta creada por el administrador.');

INSERT INTO areas (id, codigo, nombre, descripcion) VALUES
    (1, 'SOPORTE_INFORMATICO', 'Soporte informático',
     'Hardware, software, red, cuentas y equipos.'),
    (2, 'MANTENIMIENTO', 'Mantenimiento',
     'Infraestructura, mobiliario e instalaciones.'),
    (3, 'SERVICIOS_GENERALES', 'Servicios generales',
     'Limpieza, seguridad y servicios comunes.'),
    (4, 'BIBLIOTECA', 'Biblioteca',
     'Préstamos, catálogo y espacios de biblioteca.');

INSERT INTO prioridades (id, codigo, nombre, nivel, color) VALUES
    (1, 'BAJA', 'Baja', 1, '#6B7280'),
    (2, 'MEDIA', 'Media', 2, '#F59E0B'),
    (3, 'ALTA', 'Alta', 3, '#EF4444'),
    (4, 'CRITICA', 'Crítica', 4, '#991B1B');

INSERT INTO estados_solicitud
    (id, codigo, nombre, descripcion, es_final, orden) VALUES
    (1, 'NUEVO', 'Nuevo', 'Registrada y pendiente de evaluación.', 0, 1),
    (2, 'ASIGNADO', 'Asignado', 'Con técnico responsable asignado.', 0, 2),
    (3, 'EN_PROCESO', 'En proceso', 'El técnico inició la atención.', 0, 3),
    (4, 'RESUELTO', 'Resuelto', 'Atendida, pendiente de cierre por el supervisor.', 0, 4),
    (5, 'CERRADO', 'Cerrado', 'Cierre conforme; estado final.', 1, 5),
    (6, 'RECHAZADO', 'Rechazado', 'Improcedente o duplicada; estado final.', 1, 6);

INSERT INTO categorias
    (id, area_id, prioridad_predeterminada_id, codigo, nombre, descripcion) VALUES
    (1, 1, 2, 'SOPORTE_INFORMATICO', 'Soporte informático',
     'Problemas de hardware, software, red o cuentas.'),
    (2, 2, 2, 'MANTENIMIENTO', 'Mantenimiento',
     'Infraestructura, mobiliario e instalaciones.'),
    (3, 3, 1, 'SERVICIOS_GENERALES', 'Servicios generales',
     'Limpieza, seguridad y servicios comunes.'),
    (4, 4, 1, 'BIBLIOTECA', 'Biblioteca',
     'Préstamos, catálogo y espacios de biblioteca.');

INSERT INTO transiciones_estado
    (id, estado_origen_id, estado_destino_id, rol_id, requiere_motivo) VALUES
    (1, 1, 2, 3, 0),  -- NUEVO -> ASIGNADO (Supervisor)
    (2, 1, 6, 3, 1),  -- NUEVO -> RECHAZADO (Supervisor)
    (3, 2, 3, 2, 0),  -- ASIGNADO -> EN_PROCESO (Técnico)
    (4, 2, 6, 3, 1),  -- ASIGNADO -> RECHAZADO (Supervisor)
    (5, 3, 4, 2, 0),  -- EN_PROCESO -> RESUELTO (Técnico)
    (6, 3, 6, 3, 1),  -- EN_PROCESO -> RECHAZADO (Supervisor)
    (7, 4, 5, 3, 0);  -- RESUELTO -> CERRADO (Supervisor)

INSERT INTO politicas_sla
    (id, prioridad_id, categoria_id, horas_objetivo, horas_aviso) VALUES
    (1, 1, NULL, 72, 12),  -- Baja
    (2, 2, NULL, 48, 8),   -- Media
    (3, 3, NULL, 24, 4),   -- Alta
    (4, 4, NULL, 8, 2),    -- Crítica
    (5, NULL, 1, 24, 4);   -- Soporte informático (prevalece sobre prioridad)

INSERT INTO tipos_espacio (id, codigo, nombre, descripcion) VALUES
    (1, 'AULA', 'Aula', 'Espacio para clases y actividades académicas.'),
    (2, 'LABORATORIO', 'Laboratorio', 'Espacio equipado para prácticas.'),
    (3, 'SALA_ESTUDIO', 'Sala de estudio', 'Espacio para estudio individual o grupal.'),
    (4, 'AUDITORIO', 'Auditorio', 'Espacio para eventos y presentaciones.'),
    (5, 'SALA_REUNIONES', 'Sala de reuniones', 'Espacio para reuniones de trabajo.'),
    (6, 'OTRO', 'Otro', 'Ambiente no clasificado en las categorías anteriores.');

INSERT INTO estados_reserva
    (id, codigo, nombre, descripcion, es_final, orden) VALUES
    (1, 'PENDIENTE', 'Pendiente', 'Registrada y pendiente de aprobación.', 0, 1),
    (2, 'CONFIRMADA', 'Confirmada', 'Aprobada por el responsable del área.', 0, 2),
    (3, 'RECHAZADA', 'Rechazada', 'Rechazada con motivo; estado final.', 1, 3),
    (4, 'CANCELADA', 'Cancelada', 'Cancelada por el solicitante o el administrador; estado final.', 1, 4);

INSERT INTO espacios
    (id, tipo_espacio_id, area_id, codigo, nombre, descripcion, pabellon, piso,
     ubicacion_detallada, capacidad, requiere_aprobacion) VALUES
    (1, 2, 1, 'LAB-204', 'Laboratorio 204', 'Laboratorio de cómputo.',
     'Pabellón nuevo', '2', 'Laboratorio 204 - Pabellón nuevo', 30, 1),
    (2, 1, NULL, 'AULA-101', 'Aula 101', 'Aula teórica.',
     'Pabellón antiguo', '1', 'Aula 101 - Pabellón antiguo', 40, 1),
    (3, 3, 4, 'SALA-EST-01', 'Sala de estudio 1', 'Sala de estudio grupal.',
     'Biblioteca', '1', 'Sala de estudio 1 - Biblioteca', 12, 1),
    (4, 4, NULL, 'AUD-01', 'Auditorio principal', 'Auditorio para eventos.',
     'Pabellón central', '1', 'Auditorio principal - Pabellón central', 200, 1),
    (5, 5, 2, 'SALA-REU-01', 'Sala de reuniones 1', 'Sala para reuniones.',
     'Pabellón administrativo', '2', 'Sala de reuniones 1 - Pabellón administrativo', 10, 1);

INSERT INTO horarios_espacio (espacio_id, dia_semana, hora_apertura, hora_cierre)
SELECT e.id, d.dia, '07:00:00', '22:00:00'
FROM espacios e
CROSS JOIN (
    SELECT 1 AS dia UNION ALL SELECT 2 UNION ALL SELECT 3
    UNION ALL SELECT 4 UNION ALL SELECT 5
) d;

INSERT INTO horarios_espacio (espacio_id, dia_semana, hora_apertura, hora_cierre)
VALUES (3, 6, '08:00:00', '13:00:00');

INSERT INTO politicas_reserva
    (duracion_minima_minutos, duracion_maxima_minutos, dias_anticipacion_maxima,
     horas_anticipacion_minima, max_reservas_activas_por_dia, vigente_desde, activo)
VALUES
    (30, 240, 30, 1, 2, UTC_TIMESTAMP(6), 1);
