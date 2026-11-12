# Fase 02 · Solicitudes, atención y reportes

**Objetivo.** Completar las HU-03 a HU-20 y el recorrido desde el registro hasta el cierre, la auditoría, la encuesta y los indicadores.

**Entrada.** Actor autenticado, esquema y catálogos base de la [fase 01](01-fundaciones-y-sesion.md). Los contratos se organizan en [usuarios](../api/usuarios.md), [catálogos](../api/catalogos.md), [solicitudes](../api/solicitudes.md), [atención](../api/atencion.md), [calidad](../api/calidad-servicio.md) e [indicadores](../api/dashboard.md).

## Tareas

- [ ] **Usuarios y catálogos, HU-15 a HU-17.** Completar `users` y `catalogs` con alta administrativa, roles, áreas, categorías, prioridades y etiquetas de estado. Técnico y supervisor requieren área activa. El alta establece cambio obligatorio de contraseña y la desactivación revoca sesiones. Preservar códigos de estado, transiciones y referencias históricas al editar catálogos.
- [ ] **SLA y auditoría base, HU-18 y soporte de HU-19.** Implementar políticas en `sla` y escritura transaccional de eventos en `audit`. Al registrar, aplicar política activa de categoría y luego de prioridad como alternativa. Guardar plazos de aviso y vencimiento. Los cambios de política o prioridad preservan el plazo de solicitudes existentes.
- [ ] **Registro y lecturas, HU-03 a HU-06.** Implementar `POST /requests`, listado, detalle, historial y comentarios según OpenAPI. Generar el código de solicitud en la transacción del alta, con estado inicial `NUEVO`. Filtrar por propiedad, área o asignación antes de contar y paginar. La bandeja del técnico ordena por prioridad descendente, fecha ascendente y desempate por ID. Usar `RequestAccessService` para compartir las reglas de alcance entre detalle, historial y comentarios.
- [ ] **Asignación y flujo, HU-07 a HU-11.** `management` consulta técnicos elegibles y delega las escrituras a `RequestWorkflowService`. El supervisor del área asigna a técnicos activos del área. La primera asignación mueve `NUEVO` a `ASIGNADO`. La reasignación finaliza la anterior y conserva el estado. El técnico asignado puede iniciar y resolver. El supervisor cierra desde `RESUELTO` o rechaza con motivo desde `NUEVO`, `ASIGNADO` o `EN_PROCESO`. En estados terminales se bloquean nuevas transiciones y reasignaciones.
- [ ] **Mantener consistencia del flujo.** Guardar solicitud, asignación, historial de estado y auditoría en una transacción. Proteger la asignación activa con unicidad y la actualización de solicitud con control de concurrencia. Responder 409 ante cambios incompatibles. Cerrar o rechazar termina la asignación activa, limpia el responsable vigente y conserva el último responsable para consultas autorizadas.
- [ ] **Exponer auditoría y encuesta, HU-19 y HU-20.** Autorizar la lectura de eventos por rol y recurso. Registrar una encuesta por solicitud cerrada, únicamente por su propietario, con calificación de 1 a 5. La unicidad de persistencia respalda el 409 ante duplicados.
- [ ] **Indicadores y exportación, HU-12 a HU-14.** Aplicar las nueve definiciones del diseño de datos. Usar el mismo alcance y filtros en resumen y CSV, con `fechaDesde` inclusiva y `fechaHasta` exclusiva. El supervisor usa su área y recibe 403 si pide otra. El administrador puede elegir área o consultar todas. Usar el responsable actual o el último histórico de la solicitud terminal. Exponer vencimientos cuando el SLA esté operativo. Exportar solo encabezados cuando no haya datos.

## Verificación de la fase

| Caso | Resultado esperado |
| --- | --- |
| Registro, asignación, inicio, resolución y cierre | Las transiciones válidas quedan en historial y auditoría. Tras el cierre, la solicitud sale de la bandeja activa del técnico. |
| Acceso de otro estudiante, técnico o supervisor | El recurso fuera de alcance devuelve 404 y no aparece en el total de la lista. |
| Reasignación simultánea o escritura incompatible | Como máximo una asignación vigente. El conflicto devuelve 409 sin duplicar historial. |
| Fallo de historial o auditoría | La escritura de negocio se revierte junto con sus efectos. |
| SLA en límites y cambio de política | Se respeta la precedencia de categoría, los límites de aviso y vencimiento y el plazo original. |
| Rechazo sin motivo, salto de estado o encuesta duplicada | El contrato devuelve el error correspondiente sin aceptar el cambio. |
| Resumen y CSV | Coinciden en alcance y rango. Los datos de frontera y el último responsable se atribuyen correctamente. |

**Cierre.** Las operaciones de HU-03 a HU-20 están trazadas a OpenAPI y tienen pruebas de autorización, reglas de negocio e integración MySQL según corresponda.
