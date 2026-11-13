# Base de datos del backend

> Estado: implementado. Las migraciones de `src/main/resources/db/migration/` son la referencia del esquema. El diseño de partida es el [Anexo A de DISENIO-BD.md](https://github.com/taller-web-sigesu/infra-uni/blob/main/DISENIO-BD.md#15-anexo-a-ddl-completo).

## Migraciones Flyway

| Versión | Contenido |
| --- | --- |
| `V1__esquema_inicial.sql` | 26 tablas, 5 vistas, claves, checks e índices del Anexo A con los ajustes reconciliados de abajo. Sin `CREATE DATABASE` ni `USE`. |
| `V2__catalogos_semilla.sql` | Roles, áreas, prioridades, estados y transiciones, categorías, políticas SLA, tipos y estados de reserva, espacios piloto con horario y política de reservas. |
| `db/demo/V1000__datos_demostracion.sql` | Cuentas ficticias (contraseña `Demo2026!`). Solo se aplica con `APP_FLYWAY_LOCATIONS=classpath:db/migration,classpath:db/demo`. |

JPA no crea tablas (`spring-boot-starter-jdbc`, sin Hibernate). Una base vacía se migra al arrancar y un segundo arranque no reaplica nada (verificado por `scripts/smoke-image.sh`).

## Ajustes respecto del Anexo A

| Tema | Cambio | Motivo |
| --- | --- | --- |
| Refresh consumidos | Tabla `tokens_refresh_consumidos (sesion_id, hash_token único, consumido_en, expira_en)`. Se purgan cada hora los vencidos. | Detectar el reúso de un refresh rotado y revocar la sesión que lo emitió (fase 00). |
| Franjas activas | `uk_reservas_franja_exacta (espacio_id, fecha, hora_inicio, estado_id)` se reemplaza por la columna generada `franja_activa` (1 para `PENDIENTE`/`CONFIRMADA`, `NULL` si no) y `uk_reservas_franja_activa (espacio_id, fecha, hora_inicio, franja_activa)`. | La clave original impedía cancelar una segunda reserva de la misma franja liberada. Los cruces parciales se siguen validando con el espacio bloqueado. |
| Vista operativa | `vw_reservas_operativas` compara `fecha + hora` con `CONVERT_TZ(UTC_TIMESTAMP(6), '+00:00', '-05:00')`. | Las reservas se guardan en calendario de Lima (UTC−5 sin horario de verano). |
| Último responsable | Vista `vw_ultimo_responsable (solicitud_id, tecnico_id, tecnico)` con la asignación más reciente. | Detalle y dashboard atribuyen las solicitudes terminales al último técnico (sección 10.6 ajustada). |

## Convenciones de acceso

- Repositorios con `JdbcClient` y SQL explícito, dentro del módulo dueño. Los servicios de aplicación definen las transacciones.
- Instantes como `DATETIME(6)` en UTC: la sesión JDBC fija `time_zone = '+00:00'` y el código convierte `Instant` ⇄ `LocalDateTime` UTC explícitamente (`shared/persistence/Db`). Fechas y horas de reserva (`DATE`, `TIME`) son calendario de Lima.
- Concurrencia de solicitudes: `SELECT … FOR UPDATE` sobre la solicitud, `version_fila` como control optimista (0 filas → 409) y `uk_asignaciones_activa` como barrera de una sola asignación vigente.
- Concurrencia de reservas (`READ COMMITTED`): alta bloquea cuenta del solicitante y luego el espacio; decisión bloquea espacio y luego la reserva; cancelación bloquea la reserva. Los conteos de límite diario y solapamiento se leen después de los bloqueos.
- Códigos `SOL-AAAA-NNNNNN` y `RES-AAAA-NNNNNN` con `INSERT … ON DUPLICATE KEY UPDATE ultimo_valor = LAST_INSERT_ID(ultimo_valor + 1)` en la transacción del alta.
- Auditoría en `auditoria_eventos` dentro de la misma transacción (`Propagation.MANDATORY`): si falla, se revierte la operación de negocio.

## Cuentas y evidencias (HU-26 a HU-29)

- Auto-registro: `usuarios` con `estado_cuenta = 'PENDIENTE'`, `origen_registro = 'AUTO_REGISTRO'` y `debe_cambiar_contrasena = 0`. La aprobación o el rechazo guardan `revisado_por_id`, `revisado_en` y, al rechazar, `motivo_rechazo` (se limpia si luego se aprueba).
- Restablecimiento: nuevo hash, `debe_cambiar_contrasena = 1` y `revocada_en` en las sesiones vigentes, en una transacción con la cuenta bloqueada.
- Evidencias: `adjuntos_solicitud` con `proveedor_almacenamiento` (`LOCAL` o `SUPABASE`), clave `solicitudes/{solicitud_id}/{uuid}.{ext}` generada por el servidor y `hash_sha256`. La carga bloquea la solicitud (`FOR UPDATE`) antes de contar las vigentes (máximo 5), sube el objeto y luego inserta la fila; si el `INSERT` falla, borra el objeto. La eliminación es lógica (`eliminado_en`, `eliminado_por_id`) y el objeto se borra después del commit.

## Indicadores

`dashboard/repository/DashboardReadRepository` implementa las definiciones de la sección 10 con el rango `[fechaDesde, fechaHasta)` sobre `creado_en` y el mismo alcance para resumen y CSV. Vencidas y por vencer usan el reloj de la aplicación, equivalente a `vw_estado_sla`. El indicador de vencimientos se marca `disponible: false` si no hay políticas SLA activas.
