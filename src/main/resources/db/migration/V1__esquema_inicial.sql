-- Esquema inicial de Gestal (MySQL 8.4).
-- Fuente: infra-uni/DISENIO-BD.md, Anexo A, reconciliado según docs/DATABASE.md.

CREATE TABLE roles (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    codigo VARCHAR(30) NOT NULL,
    nombre VARCHAR(60) NOT NULL,
    descripcion VARCHAR(255) NULL,
    activo TINYINT UNSIGNED NOT NULL DEFAULT 1,
    creado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    actualizado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_roles_codigo (codigo),
    UNIQUE KEY uk_roles_nombre (nombre),
    CONSTRAINT chk_roles_activo CHECK (activo IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE areas (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    codigo VARCHAR(50) NOT NULL,
    nombre VARCHAR(120) NOT NULL,
    descripcion VARCHAR(255) NULL,
    activo TINYINT UNSIGNED NOT NULL DEFAULT 1,
    creado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    actualizado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_areas_codigo (codigo),
    UNIQUE KEY uk_areas_nombre (nombre),
    CONSTRAINT chk_areas_activo CHECK (activo IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE usuarios (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    rol_id BIGINT UNSIGNED NOT NULL,
    area_id BIGINT UNSIGNED NULL,
    nombre_completo VARCHAR(150) NOT NULL,
    correo VARCHAR(150) NOT NULL,
    hash_contrasena VARCHAR(255) NOT NULL,
    telefono VARCHAR(20) NULL,
    activo TINYINT UNSIGNED NOT NULL DEFAULT 1,
    estado_cuenta VARCHAR(20) NOT NULL DEFAULT 'ACTIVA',
    origen_registro VARCHAR(20) NOT NULL DEFAULT 'ADMINISTRADOR',
    revisado_por_id BIGINT UNSIGNED NULL,
    revisado_en DATETIME(6) NULL,
    motivo_rechazo TEXT NULL,
    debe_cambiar_contrasena TINYINT UNSIGNED NOT NULL DEFAULT 1,
    ultimo_acceso_en DATETIME(6) NULL,
    creado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    actualizado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_usuarios_correo (correo),
    KEY idx_usuarios_rol_activo (rol_id, activo),
    KEY idx_usuarios_area_activo (area_id, activo),
    KEY idx_usuarios_estado_cuenta (estado_cuenta, creado_en),
    CONSTRAINT fk_usuarios_rol FOREIGN KEY (rol_id)
        REFERENCES roles (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_usuarios_area FOREIGN KEY (area_id)
        REFERENCES areas (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_usuarios_activo CHECK (activo IN (0, 1)),
    CONSTRAINT fk_usuarios_revisor FOREIGN KEY (revisado_por_id)
        REFERENCES usuarios (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_usuarios_debe_cambiar
        CHECK (debe_cambiar_contrasena IN (0, 1)),
    CONSTRAINT chk_usuarios_estado_cuenta
        CHECK (estado_cuenta IN ('PENDIENTE', 'ACTIVA', 'RECHAZADA')),
    CONSTRAINT chk_usuarios_origen
        CHECK (origen_registro IN ('ADMINISTRADOR', 'AUTO_REGISTRO')),
    CONSTRAINT chk_usuarios_motivo_rechazo
        CHECK (estado_cuenta <> 'RECHAZADA'
               OR (motivo_rechazo IS NOT NULL AND CHAR_LENGTH(TRIM(motivo_rechazo)) > 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sesiones_usuario (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    usuario_id BIGINT UNSIGNED NOT NULL,
    hash_token CHAR(64) NOT NULL,
    emitida_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    expira_en DATETIME(6) NOT NULL,
    revocada_en DATETIME(6) NULL,
    ip VARCHAR(45) NULL,
    agente_usuario VARCHAR(255) NULL,
    creado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_sesiones_token (hash_token),
    KEY idx_sesiones_usuario (usuario_id, revocada_en),
    CONSTRAINT fk_sesiones_usuario FOREIGN KEY (usuario_id)
        REFERENCES usuarios (id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE tokens_refresh_consumidos (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    sesion_id BIGINT UNSIGNED NOT NULL,
    hash_token CHAR(64) NOT NULL,
    consumido_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    expira_en DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tokens_consumidos_hash (hash_token),
    KEY idx_tokens_consumidos_sesion (sesion_id),
    KEY idx_tokens_consumidos_expira (expira_en),
    CONSTRAINT fk_tokens_consumidos_sesion FOREIGN KEY (sesion_id)
        REFERENCES sesiones_usuario (id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE prioridades (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    codigo VARCHAR(30) NOT NULL,
    nombre VARCHAR(60) NOT NULL,
    nivel TINYINT UNSIGNED NOT NULL,
    color CHAR(7) NULL,
    activo TINYINT UNSIGNED NOT NULL DEFAULT 1,
    creado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    actualizado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_prioridades_codigo (codigo),
    UNIQUE KEY uk_prioridades_nombre (nombre),
    UNIQUE KEY uk_prioridades_nivel (nivel),
    CONSTRAINT chk_prioridades_activo CHECK (activo IN (0, 1)),
    CONSTRAINT chk_prioridades_nivel CHECK (nivel BETWEEN 1 AND 10)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE estados_solicitud (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    codigo VARCHAR(30) NOT NULL,
    nombre VARCHAR(60) NOT NULL,
    descripcion VARCHAR(255) NULL,
    es_final TINYINT UNSIGNED NOT NULL DEFAULT 0,
    orden TINYINT UNSIGNED NOT NULL,
    activo TINYINT UNSIGNED NOT NULL DEFAULT 1,
    creado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    actualizado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_estados_codigo (codigo),
    UNIQUE KEY uk_estados_orden (orden),
    CONSTRAINT chk_estados_final CHECK (es_final IN (0, 1)),
    CONSTRAINT chk_estados_activo CHECK (activo IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE categorias (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    area_id BIGINT UNSIGNED NOT NULL,
    prioridad_predeterminada_id BIGINT UNSIGNED NOT NULL,
    codigo VARCHAR(50) NOT NULL,
    nombre VARCHAR(120) NOT NULL,
    descripcion VARCHAR(255) NULL,
    activo TINYINT UNSIGNED NOT NULL DEFAULT 1,
    creado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    actualizado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_categorias_codigo (codigo),
    UNIQUE KEY uk_categorias_nombre (nombre),
    KEY idx_categorias_area_activo (area_id, activo),
    KEY idx_categorias_prioridad (prioridad_predeterminada_id),
    CONSTRAINT fk_categorias_area FOREIGN KEY (area_id)
        REFERENCES areas (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_categorias_prioridad FOREIGN KEY (prioridad_predeterminada_id)
        REFERENCES prioridades (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_categorias_activo CHECK (activo IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE transiciones_estado (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    estado_origen_id BIGINT UNSIGNED NOT NULL,
    estado_destino_id BIGINT UNSIGNED NOT NULL,
    rol_id BIGINT UNSIGNED NOT NULL,
    requiere_motivo TINYINT UNSIGNED NOT NULL DEFAULT 0,
    activo TINYINT UNSIGNED NOT NULL DEFAULT 1,
    creado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    actualizado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_transiciones (estado_origen_id, estado_destino_id, rol_id),
    KEY idx_transiciones_origen (estado_origen_id, activo),
    KEY idx_transiciones_destino (estado_destino_id, activo),
    KEY idx_transiciones_rol (rol_id, activo),
    CONSTRAINT fk_transiciones_origen FOREIGN KEY (estado_origen_id)
        REFERENCES estados_solicitud (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_transiciones_destino FOREIGN KEY (estado_destino_id)
        REFERENCES estados_solicitud (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_transiciones_rol FOREIGN KEY (rol_id)
        REFERENCES roles (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_transiciones_motivo CHECK (requiere_motivo IN (0, 1)),
    CONSTRAINT chk_transiciones_activo CHECK (activo IN (0, 1)),
    CONSTRAINT chk_transiciones_distintos
        CHECK (estado_origen_id <> estado_destino_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE politicas_sla (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    prioridad_id BIGINT UNSIGNED NULL,
    categoria_id BIGINT UNSIGNED NULL,
    horas_objetivo SMALLINT UNSIGNED NOT NULL,
    horas_aviso SMALLINT UNSIGNED NOT NULL,
    vigente_desde DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    vigente_hasta DATETIME(6) NULL,
    activo TINYINT UNSIGNED NOT NULL DEFAULT 1,
    creado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    actualizado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_politicas_prioridad (prioridad_id, activo),
    KEY idx_politicas_categoria (categoria_id, activo),
    CONSTRAINT fk_politicas_prioridad FOREIGN KEY (prioridad_id)
        REFERENCES prioridades (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_politicas_categoria FOREIGN KEY (categoria_id)
        REFERENCES categorias (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_politicas_alcance CHECK (
        (prioridad_id IS NOT NULL AND categoria_id IS NULL)
        OR (prioridad_id IS NULL AND categoria_id IS NOT NULL)
    ),
    CONSTRAINT chk_politicas_horas CHECK (
        horas_objetivo > 0
        AND horas_aviso >= 0
        AND horas_aviso < horas_objetivo
    ),
    CONSTRAINT chk_politicas_vigencia CHECK (
        vigente_hasta IS NULL OR vigente_hasta > vigente_desde
    ),
    CONSTRAINT chk_politicas_activo CHECK (activo IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE solicitudes (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    codigo VARCHAR(20) NOT NULL,
    solicitante_id BIGINT UNSIGNED NOT NULL,
    categoria_id BIGINT UNSIGNED NOT NULL,
    area_id BIGINT UNSIGNED NOT NULL,
    prioridad_id BIGINT UNSIGNED NOT NULL,
    estado_id BIGINT UNSIGNED NOT NULL,
    responsable_actual_id BIGINT UNSIGNED NULL,
    politica_sla_id BIGINT UNSIGNED NULL,
    ubicacion VARCHAR(255) NOT NULL,
    descripcion TEXT NOT NULL,
    motivo_rechazo TEXT NULL,
    fecha_limite_sla DATETIME(6) NULL,
    fecha_aviso_sla DATETIME(6) NULL,
    resuelta_en DATETIME(6) NULL,
    cerrada_en DATETIME(6) NULL,
    version_fila INT UNSIGNED NOT NULL DEFAULT 0,
    creado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    actualizado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_solicitudes_codigo (codigo),
    KEY idx_solicitudes_solicitante (solicitante_id, creado_en),
    KEY idx_solicitudes_area_estado_prioridad
        (area_id, estado_id, prioridad_id, creado_en),
    KEY idx_solicitudes_responsable
        (responsable_actual_id, estado_id, prioridad_id, creado_en),
    KEY idx_solicitudes_categoria (categoria_id, creado_en),
    KEY idx_solicitudes_sla (fecha_limite_sla, estado_id),
    KEY idx_solicitudes_creado (creado_en),
    CONSTRAINT fk_solicitudes_solicitante FOREIGN KEY (solicitante_id)
        REFERENCES usuarios (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_solicitudes_categoria FOREIGN KEY (categoria_id)
        REFERENCES categorias (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_solicitudes_area FOREIGN KEY (area_id)
        REFERENCES areas (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_solicitudes_prioridad FOREIGN KEY (prioridad_id)
        REFERENCES prioridades (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_solicitudes_estado FOREIGN KEY (estado_id)
        REFERENCES estados_solicitud (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_solicitudes_responsable FOREIGN KEY (responsable_actual_id)
        REFERENCES usuarios (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_solicitudes_politica FOREIGN KEY (politica_sla_id)
        REFERENCES politicas_sla (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_solicitudes_descripcion
        CHECK (CHAR_LENGTH(TRIM(descripcion)) > 0),
    CONSTRAINT chk_solicitudes_ubicacion
        CHECK (CHAR_LENGTH(TRIM(ubicacion)) > 0),
    CONSTRAINT chk_solicitudes_fechas CHECK (
        (resuelta_en IS NULL OR resuelta_en >= creado_en)
        AND (cerrada_en IS NULL OR cerrada_en >= creado_en)
        AND (cerrada_en IS NULL OR resuelta_en IS NULL OR cerrada_en >= resuelta_en)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE historial_estados (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    solicitud_id BIGINT UNSIGNED NOT NULL,
    estado_origen_id BIGINT UNSIGNED NULL,
    estado_destino_id BIGINT UNSIGNED NOT NULL,
    cambiado_por_id BIGINT UNSIGNED NOT NULL,
    motivo TEXT NULL,
    cambiado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_historial_solicitud (solicitud_id, cambiado_en),
    KEY idx_historial_origen (estado_origen_id),
    KEY idx_historial_destino (estado_destino_id),
    KEY idx_historial_usuario (cambiado_por_id),
    CONSTRAINT fk_historial_solicitud FOREIGN KEY (solicitud_id)
        REFERENCES solicitudes (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_historial_origen FOREIGN KEY (estado_origen_id)
        REFERENCES estados_solicitud (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_historial_destino FOREIGN KEY (estado_destino_id)
        REFERENCES estados_solicitud (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_historial_usuario FOREIGN KEY (cambiado_por_id)
        REFERENCES usuarios (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_historial_distintos CHECK (
        estado_origen_id IS NULL OR estado_origen_id <> estado_destino_id
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE asignaciones (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    solicitud_id BIGINT UNSIGNED NOT NULL,
    tecnico_id BIGINT UNSIGNED NOT NULL,
    asignado_por_id BIGINT UNSIGNED NOT NULL,
    asignada_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    finalizada_en DATETIME(6) NULL,
    motivo_fin VARCHAR(255) NULL,
    solicitud_activa_id BIGINT UNSIGNED GENERATED ALWAYS AS (
        CASE WHEN finalizada_en IS NULL THEN solicitud_id ELSE NULL END
    ) STORED,
    creado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_asignaciones_activa (solicitud_activa_id),
    KEY idx_asignaciones_tecnico (tecnico_id, finalizada_en),
    KEY idx_asignaciones_solicitud (solicitud_id, asignada_en),
    KEY idx_asignaciones_asignador (asignado_por_id),
    CONSTRAINT fk_asignaciones_solicitud FOREIGN KEY (solicitud_id)
        REFERENCES solicitudes (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_asignaciones_tecnico FOREIGN KEY (tecnico_id)
        REFERENCES usuarios (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_asignaciones_asignador FOREIGN KEY (asignado_por_id)
        REFERENCES usuarios (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_asignaciones_fechas CHECK (
        finalizada_en IS NULL OR finalizada_en >= asignada_en
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE comentarios (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    solicitud_id BIGINT UNSIGNED NOT NULL,
    autor_id BIGINT UNSIGNED NOT NULL,
    contenido TEXT NOT NULL,
    creado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    editado_en DATETIME(6) NULL,
    PRIMARY KEY (id),
    KEY idx_comentarios_solicitud (solicitud_id, creado_en),
    KEY idx_comentarios_autor (autor_id),
    CONSTRAINT fk_comentarios_solicitud FOREIGN KEY (solicitud_id)
        REFERENCES solicitudes (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_comentarios_autor FOREIGN KEY (autor_id)
        REFERENCES usuarios (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_comentarios_contenido
        CHECK (CHAR_LENGTH(TRIM(contenido)) > 0),
    CONSTRAINT chk_comentarios_edicion
        CHECK (editado_en IS NULL OR editado_en >= creado_en)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE encuestas_satisfaccion (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    solicitud_id BIGINT UNSIGNED NOT NULL,
    respondida_por_id BIGINT UNSIGNED NOT NULL,
    calificacion TINYINT UNSIGNED NOT NULL,
    comentario TEXT NULL,
    respondida_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_encuestas_solicitud (solicitud_id),
    KEY idx_encuestas_respondedor (respondida_por_id),
    CONSTRAINT fk_encuestas_solicitud FOREIGN KEY (solicitud_id)
        REFERENCES solicitudes (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_encuestas_respondedor FOREIGN KEY (respondida_por_id)
        REFERENCES usuarios (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_encuestas_calificacion
        CHECK (calificacion BETWEEN 1 AND 5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE auditoria_eventos (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    entidad VARCHAR(60) NOT NULL,
    entidad_id BIGINT UNSIGNED NOT NULL,
    solicitud_id BIGINT UNSIGNED NULL,
    accion VARCHAR(30) NOT NULL,
    campo VARCHAR(80) NULL,
    valor_anterior TEXT NULL,
    valor_nuevo TEXT NULL,
    actor_id BIGINT UNSIGNED NULL,
    ocurrido_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    id_correlacion CHAR(36) NULL,
    ip VARCHAR(45) NULL,
    PRIMARY KEY (id),
    KEY idx_auditoria_solicitud (solicitud_id, ocurrido_en),
    KEY idx_auditoria_entidad (entidad, entidad_id, ocurrido_en),
    KEY idx_auditoria_actor (actor_id, ocurrido_en),
    KEY idx_auditoria_fecha (ocurrido_en),
    CONSTRAINT fk_auditoria_solicitud FOREIGN KEY (solicitud_id)
        REFERENCES solicitudes (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_auditoria_actor FOREIGN KEY (actor_id)
        REFERENCES usuarios (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_auditoria_entidad
        CHECK (CHAR_LENGTH(TRIM(entidad)) > 0),
    CONSTRAINT chk_auditoria_accion
        CHECK (CHAR_LENGTH(TRIM(accion)) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE secuencias_codigo_solicitud (
    anio SMALLINT UNSIGNED NOT NULL,
    ultimo_valor INT UNSIGNED NOT NULL DEFAULT 0,
    actualizado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (anio)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE OR REPLACE VIEW vw_solicitudes_detalle AS
SELECT
    s.id,
    s.codigo,
    s.solicitante_id,
    sol.nombre_completo AS solicitante,
    s.categoria_id,
    c.nombre AS categoria,
    s.area_id,
    a.nombre AS area,
    s.prioridad_id,
    p.nombre AS prioridad,
    p.nivel AS prioridad_nivel,
    s.estado_id,
    e.codigo AS estado_codigo,
    e.nombre AS estado,
    e.es_final,
    s.responsable_actual_id,
    tec.nombre_completo AS responsable,
    s.ubicacion,
    s.descripcion,
    s.motivo_rechazo,
    s.fecha_limite_sla,
    s.fecha_aviso_sla,
    s.resuelta_en,
    s.cerrada_en,
    s.creado_en,
    s.actualizado_en
FROM solicitudes s
JOIN usuarios sol ON sol.id = s.solicitante_id
JOIN categorias c ON c.id = s.categoria_id
JOIN areas a ON a.id = s.area_id
JOIN prioridades p ON p.id = s.prioridad_id
JOIN estados_solicitud e ON e.id = s.estado_id
LEFT JOIN usuarios tec ON tec.id = s.responsable_actual_id;

CREATE OR REPLACE VIEW vw_estado_sla AS
SELECT
    s.id AS solicitud_id,
    s.codigo,
    s.estado_id,
    e.codigo AS estado_codigo,
    e.es_final,
    s.fecha_limite_sla,
    s.fecha_aviso_sla,
    CASE
        WHEN e.es_final = 1 THEN 'CERRADA'
        WHEN s.fecha_limite_sla IS NULL THEN 'SIN_SLA'
        WHEN UTC_TIMESTAMP(6) > s.fecha_limite_sla THEN 'VENCIDA'
        WHEN s.fecha_aviso_sla IS NOT NULL
             AND UTC_TIMESTAMP(6) >= s.fecha_aviso_sla THEN 'POR_VENCER'
        ELSE 'EN_PLAZO'
    END AS estado_sla,
    CASE
        WHEN e.codigo = 'CERRADO'
             AND s.fecha_limite_sla IS NOT NULL
             AND s.resuelta_en IS NOT NULL
        THEN (s.resuelta_en <= s.fecha_limite_sla)
        ELSE NULL
    END AS cumplio_sla
FROM solicitudes s
JOIN estados_solicitud e ON e.id = s.estado_id;

CREATE TABLE tipos_espacio (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    codigo VARCHAR(40) NOT NULL,
    nombre VARCHAR(80) NOT NULL,
    descripcion VARCHAR(255) NULL,
    activo TINYINT UNSIGNED NOT NULL DEFAULT 1,
    creado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    actualizado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_tipos_espacio_codigo (codigo),
    UNIQUE KEY uk_tipos_espacio_nombre (nombre),
    CONSTRAINT chk_tipos_espacio_activo CHECK (activo IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE espacios (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    tipo_espacio_id BIGINT UNSIGNED NOT NULL,
    area_id BIGINT UNSIGNED NULL,
    codigo VARCHAR(40) NOT NULL,
    nombre VARCHAR(120) NOT NULL,
    descripcion VARCHAR(255) NULL,
    pabellon VARCHAR(60) NOT NULL DEFAULT '',
    piso VARCHAR(20) NULL,
    ubicacion_detallada VARCHAR(255) NULL,
    capacidad SMALLINT UNSIGNED NOT NULL,
    requiere_aprobacion TINYINT UNSIGNED NOT NULL DEFAULT 1,
    activo TINYINT UNSIGNED NOT NULL DEFAULT 1,
    creado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    actualizado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_espacios_codigo (codigo),
    UNIQUE KEY uk_espacios_nombre_pabellon (nombre, pabellon),
    KEY idx_espacios_tipo_activo (tipo_espacio_id, activo),
    KEY idx_espacios_area_activo (area_id, activo),
    KEY idx_espacios_pabellon_activo (pabellon, activo),
    CONSTRAINT fk_espacios_tipo FOREIGN KEY (tipo_espacio_id)
        REFERENCES tipos_espacio (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_espacios_area FOREIGN KEY (area_id)
        REFERENCES areas (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_espacios_capacidad CHECK (capacidad > 0),
    CONSTRAINT chk_espacios_requiere_aprobacion
        CHECK (requiere_aprobacion IN (0, 1)),
    CONSTRAINT chk_espacios_activo CHECK (activo IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE horarios_espacio (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    espacio_id BIGINT UNSIGNED NOT NULL,
    dia_semana TINYINT UNSIGNED NOT NULL,
    hora_apertura TIME NOT NULL,
    hora_cierre TIME NOT NULL,
    activo TINYINT UNSIGNED NOT NULL DEFAULT 1,
    creado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    actualizado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_horarios_espacio_dia (espacio_id, dia_semana),
    CONSTRAINT fk_horarios_espacio FOREIGN KEY (espacio_id)
        REFERENCES espacios (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_horarios_dia CHECK (dia_semana BETWEEN 1 AND 7),
    CONSTRAINT chk_horarios_horas CHECK (hora_cierre > hora_apertura),
    CONSTRAINT chk_horarios_activo CHECK (activo IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE estados_reserva (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    codigo VARCHAR(30) NOT NULL,
    nombre VARCHAR(60) NOT NULL,
    descripcion VARCHAR(255) NULL,
    es_final TINYINT UNSIGNED NOT NULL DEFAULT 0,
    orden TINYINT UNSIGNED NOT NULL,
    activo TINYINT UNSIGNED NOT NULL DEFAULT 1,
    creado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    actualizado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_estados_reserva_codigo (codigo),
    UNIQUE KEY uk_estados_reserva_orden (orden),
    CONSTRAINT chk_estados_reserva_final CHECK (es_final IN (0, 1)),
    CONSTRAINT chk_estados_reserva_activo CHECK (activo IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE politicas_reserva (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    duracion_minima_minutos SMALLINT UNSIGNED NOT NULL DEFAULT 30,
    duracion_maxima_minutos SMALLINT UNSIGNED NOT NULL DEFAULT 240,
    dias_anticipacion_maxima SMALLINT UNSIGNED NOT NULL DEFAULT 30,
    horas_anticipacion_minima SMALLINT UNSIGNED NOT NULL DEFAULT 1,
    max_reservas_activas_por_dia TINYINT UNSIGNED NOT NULL DEFAULT 2,
    vigente_desde DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    vigente_hasta DATETIME(6) NULL,
    activo TINYINT UNSIGNED NOT NULL DEFAULT 1,
    creado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    actualizado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_politicas_reserva_vigente (activo, vigente_desde),
    CONSTRAINT chk_politicas_reserva_duracion CHECK (
        duracion_minima_minutos > 0
        AND duracion_maxima_minutos >= duracion_minima_minutos
    ),
    CONSTRAINT chk_politicas_reserva_vigencia CHECK (
        vigente_hasta IS NULL OR vigente_hasta > vigente_desde
    ),
    CONSTRAINT chk_politicas_reserva_activo CHECK (activo IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE reservas (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    codigo VARCHAR(20) NOT NULL,
    espacio_id BIGINT UNSIGNED NOT NULL,
    solicitante_id BIGINT UNSIGNED NOT NULL,
    estado_id BIGINT UNSIGNED NOT NULL,
    fecha DATE NOT NULL,
    hora_inicio TIME NOT NULL,
    hora_fin TIME NOT NULL,
    motivo VARCHAR(255) NOT NULL,
    num_asistentes SMALLINT UNSIGNED NULL,
    aprobada_por_id BIGINT UNSIGNED NULL,
    aprobada_en DATETIME(6) NULL,
    motivo_rechazo TEXT NULL,
    cancelada_por_id BIGINT UNSIGNED NULL,
    cancelada_en DATETIME(6) NULL,
    version_fila INT UNSIGNED NOT NULL DEFAULT 0,
    franja_activa TINYINT UNSIGNED GENERATED ALWAYS AS (
        CASE WHEN estado_id IN (1, 2) THEN 1 ELSE NULL END
    ) STORED,
    creado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    actualizado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_reservas_codigo (codigo),
    UNIQUE KEY uk_reservas_franja_activa (espacio_id, fecha, hora_inicio, franja_activa),
    KEY idx_reservas_disponibilidad
        (espacio_id, fecha, estado_id, hora_inicio, hora_fin),
    KEY idx_reservas_solicitante (solicitante_id, fecha),
    KEY idx_reservas_estado_fecha (estado_id, fecha),
    KEY idx_reservas_aprobador (aprobada_por_id, aprobada_en),
    CONSTRAINT fk_reservas_espacio FOREIGN KEY (espacio_id)
        REFERENCES espacios (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_reservas_solicitante FOREIGN KEY (solicitante_id)
        REFERENCES usuarios (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_reservas_estado FOREIGN KEY (estado_id)
        REFERENCES estados_reserva (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_reservas_aprobador FOREIGN KEY (aprobada_por_id)
        REFERENCES usuarios (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_reservas_cancelador FOREIGN KEY (cancelada_por_id)
        REFERENCES usuarios (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_reservas_horas CHECK (hora_fin > hora_inicio),
    CONSTRAINT chk_reservas_motivo CHECK (CHAR_LENGTH(TRIM(motivo)) > 0),
    CONSTRAINT chk_reservas_asistentes
        CHECK (num_asistentes IS NULL OR num_asistentes > 0),
    CONSTRAINT chk_reservas_aprobacion CHECK (
        (aprobada_por_id IS NULL AND aprobada_en IS NULL)
        OR (aprobada_por_id IS NOT NULL AND aprobada_en IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE historial_estados_reserva (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    reserva_id BIGINT UNSIGNED NOT NULL,
    estado_origen_id BIGINT UNSIGNED NULL,
    estado_destino_id BIGINT UNSIGNED NOT NULL,
    cambiado_por_id BIGINT UNSIGNED NOT NULL,
    motivo TEXT NULL,
    cambiado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_historial_reserva (reserva_id, cambiado_en),
    KEY idx_historial_reserva_origen (estado_origen_id),
    KEY idx_historial_reserva_destino (estado_destino_id),
    KEY idx_historial_reserva_usuario (cambiado_por_id),
    CONSTRAINT fk_historial_reserva FOREIGN KEY (reserva_id)
        REFERENCES reservas (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_historial_reserva_origen FOREIGN KEY (estado_origen_id)
        REFERENCES estados_reserva (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_historial_reserva_destino FOREIGN KEY (estado_destino_id)
        REFERENCES estados_reserva (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_historial_reserva_usuario FOREIGN KEY (cambiado_por_id)
        REFERENCES usuarios (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_historial_reserva_distintos CHECK (
        estado_origen_id IS NULL OR estado_origen_id <> estado_destino_id
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE secuencias_codigo_reserva (
    anio SMALLINT UNSIGNED NOT NULL,
    ultimo_valor INT UNSIGNED NOT NULL DEFAULT 0,
    actualizado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (anio)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE adjuntos_solicitud (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    solicitud_id BIGINT UNSIGNED NOT NULL,
    autor_id BIGINT UNSIGNED NOT NULL,
    nombre_original VARCHAR(255) NOT NULL,
    proveedor_almacenamiento VARCHAR(20) NOT NULL DEFAULT 'SUPABASE',
    clave_objeto VARCHAR(500) NOT NULL,
    tipo_mime VARCHAR(100) NOT NULL,
    tamano_bytes INT UNSIGNED NOT NULL,
    hash_sha256 CHAR(64) NOT NULL,
    creado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    eliminado_en DATETIME(6) NULL,
    eliminado_por_id BIGINT UNSIGNED NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_adjuntos_clave_objeto (clave_objeto),
    KEY idx_adjuntos_solicitud (solicitud_id, eliminado_en, creado_en),
    KEY idx_adjuntos_autor (autor_id),
    CONSTRAINT fk_adjuntos_solicitud FOREIGN KEY (solicitud_id)
        REFERENCES solicitudes (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_adjuntos_autor FOREIGN KEY (autor_id)
        REFERENCES usuarios (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_adjuntos_eliminado_por FOREIGN KEY (eliminado_por_id)
        REFERENCES usuarios (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_adjuntos_nombre
        CHECK (CHAR_LENGTH(TRIM(nombre_original)) > 0),
    CONSTRAINT chk_adjuntos_proveedor
        CHECK (proveedor_almacenamiento IN ('SUPABASE', 'LOCAL')),
    CONSTRAINT chk_adjuntos_tipo
        CHECK (tipo_mime IN ('image/jpeg', 'image/png', 'application/pdf')),
    CONSTRAINT chk_adjuntos_tamano
        CHECK (tamano_bytes BETWEEN 1 AND 5242880),
    CONSTRAINT chk_adjuntos_eliminacion
        CHECK ((eliminado_en IS NULL AND eliminado_por_id IS NULL)
               OR (eliminado_en IS NOT NULL AND eliminado_por_id IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE OR REPLACE VIEW vw_reservas_detalle AS
SELECT
    r.id,
    r.codigo,
    r.espacio_id,
    esp.nombre AS espacio,
    te.nombre AS tipo_espacio,
    a.nombre AS area,
    r.solicitante_id,
    sol.nombre_completo AS solicitante,
    r.fecha,
    r.hora_inicio,
    r.hora_fin,
    r.motivo,
    r.num_asistentes,
    r.estado_id,
    er.codigo AS estado_codigo,
    er.nombre AS estado,
    er.es_final,
    r.aprobada_por_id,
    apr.nombre_completo AS aprobador,
    r.aprobada_en,
    r.motivo_rechazo,
    r.cancelada_por_id,
    r.cancelada_en,
    r.creado_en,
    r.actualizado_en
FROM reservas r
JOIN espacios esp ON esp.id = r.espacio_id
JOIN tipos_espacio te ON te.id = esp.tipo_espacio_id
LEFT JOIN areas a ON a.id = esp.area_id
JOIN usuarios sol ON sol.id = r.solicitante_id
JOIN estados_reserva er ON er.id = r.estado_id
LEFT JOIN usuarios apr ON apr.id = r.aprobada_por_id;

CREATE OR REPLACE VIEW vw_reservas_operativas AS
SELECT
    r.id AS reserva_id,
    r.codigo,
    r.espacio_id,
    r.fecha,
    r.hora_inicio,
    r.hora_fin,
    r.estado_id,
    er.codigo AS estado_codigo,
    CASE
        WHEN er.codigo <> 'CONFIRMADA' THEN er.codigo
        WHEN TIMESTAMP(r.fecha, r.hora_fin) <= CONVERT_TZ(UTC_TIMESTAMP(6), '+00:00', '-05:00') THEN 'FINALIZADA'
        WHEN TIMESTAMP(r.fecha, r.hora_inicio) <= CONVERT_TZ(UTC_TIMESTAMP(6), '+00:00', '-05:00') THEN 'EN_CURSO'
        ELSE 'PROXIMA'
    END AS estado_operativo
FROM reservas r
JOIN estados_reserva er ON er.id = r.estado_id;

CREATE OR REPLACE VIEW vw_ultimo_responsable AS
SELECT a.solicitud_id, a.tecnico_id, u.nombre_completo AS tecnico
FROM asignaciones a
JOIN usuarios u ON u.id = a.tecnico_id
WHERE a.id = (
    SELECT a2.id FROM asignaciones a2
    WHERE a2.solicitud_id = a.solicitud_id
    ORDER BY a2.asignada_en DESC, a2.id DESC
    LIMIT 1
);
