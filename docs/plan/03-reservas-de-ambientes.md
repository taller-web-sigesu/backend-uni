# Fase 03 · Reservas de ambientes

**Objetivo.** Implementar HU-21 a HU-25 con disponibilidad, administración, decisiones y cancelación consistentes bajo concurrencia.

**Entrada.** Identidad y permisos de la [fase 01](01-fundaciones-y-sesion.md), catálogos y auditoría de la [fase 02](02-solicitudes-atencion-y-reportes.md). Consultar el [contrato de reservas](../api/reservas.md), el [ADR de ambientes](../../../infra-uni/docs/adr/ADR-0001-reserva-ambientes.md) y las reglas de reservas del [diseño de datos](../../../infra-uni/DISENIO-BD.md).

## Tareas

- [ ] **Configurar espacios y políticas, HU-25.** Implementar en `reservations` las operaciones administrativas de espacios, tipos, horarios y política global del contrato. Mantener una franja semanal activa por espacio y día. Validar límites y conservar referencias de reservas existentes al desactivar espacios o tipos.
- [ ] **Consultar espacios y disponibilidad, HU-21.** Implementar `GET /spaces` y `GET /spaces/{id}/availability` sobre espacios activos. Si no existe horario activo para el día, el espacio está cerrado. Usar fecha y horas de Lima y comparar las franjas como intervalos con inicio inclusivo y fin exclusivo. Las reservas pendientes o confirmadas bloquean disponibilidad. Aplicar el horario semanal del piloto, incluidas fechas sin excepciones configuradas.
- [ ] **Registrar reservas, HU-22.** En `POST /reservations`, bloquear primero la fila del solicitante y luego el espacio. Validar que la franja sea futura, esté dentro del horario, cumpla duración, anticipación, capacidad y límite diario y no se solape. Consultar límites y cruces con lecturas que vean los cambios confirmados antes de decidir. Crear en `PENDIENTE` y guardar código, historial y auditoría en la misma transacción.
- [ ] **Aprobar o rechazar, HU-23.** Usar `PATCH /reservations/{id}/status` según contrato. Solo supervisor del área o administrador decide sobre una pendiente. Impedir la autoaprobación. Al confirmar, bloquear el espacio y comprobar solapamientos excluyendo la propia reserva. Al rechazar, exigir motivo. Evitar que dos escrituras concurrentes sobrescriban el estado y devolver 409 cuando corresponda.
- [ ] **Consultar y cancelar, HU-24.** Implementar listado, detalle e historial con el alcance pactado. Solo el propietario cancela una reserva pendiente o confirmada antes de la hora de inicio. El administrador conserva las acciones de decisión autorizadas por HU-23. La cancelación registra historial y auditoría y libera la franja.
- [ ] **Verificar consistencia del esquema.** Restringir la clave de franja exacta a reservas activas y mantener la comprobación transaccional de cruces parciales. Ajustar la vista operativa al calendario de Lima. Mantener los instantes de auditoría en UTC.

## Verificación de la fase

| Caso | Resultado esperado |
| --- | --- |
| Día sin horario activo o franja que ya empezó | No se ofrece disponibilidad válida ni se permite el alta. |
| Franjas contiguas y parcialmente solapadas | Las contiguas se permiten y las solapadas se rechazan. |
| Dos altas concurrentes para un espacio | No se crean dos reservas activas que se crucen. |
| Límite diario concurrente en espacios distintos | El bloqueo por solicitante impide que se supere el máximo. |
| Aprobación propia o con conflicto sobrevenido | Se deniega la decisión y se conserva el estado anterior. |
| Rechazo sin motivo o escritura concurrente | El error corresponde al contrato y no queda historial de un cambio fallido. |
| Cancelación por tercero, incluso administrador | Se deniega la cancelación. El propietario autorizado puede cancelar antes del inicio. |
| Cancelar, reservar de nuevo y volver a cancelar | La restricción de unicidad no bloquea el uso de una franja liberada. |

**Cierre.** Los escenarios pasan contra MySQL con transacciones concurrentes reales y las operaciones se pueden recorrer con los roles documentados.
