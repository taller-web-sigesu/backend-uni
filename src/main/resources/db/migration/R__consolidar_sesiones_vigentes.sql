-- Se ejecuta después de las migraciones versionadas, también en bases con datos demo.
-- Se conserva la sesión vigente más reciente de cada cuenta.
UPDATE sesiones_usuario AS sesion
JOIN (
    SELECT id, ROW_NUMBER() OVER (PARTITION BY usuario_id ORDER BY emitida_en DESC, id DESC) AS orden
    FROM sesiones_usuario
    WHERE revocada_en IS NULL AND expira_en > UTC_TIMESTAMP(6)
) AS vigentes ON vigentes.id = sesion.id
SET sesion.revocada_en = UTC_TIMESTAMP(6)
WHERE vigentes.orden > 1 AND sesion.revocada_en IS NULL AND sesion.expira_en > UTC_TIMESTAMP(6);
