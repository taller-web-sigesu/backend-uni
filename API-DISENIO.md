# Diseño de la API de Gestal

> Propuesta de contrato de alto nivel para las HU-01 a HU-25, sujeta a aprobación del equipo. El código actual define `GET /api/v1/status`. Las rutas y reglas de negocio de este documento son propuestas para el futuro contrato OpenAPI.

El contrato se organiza por caso de uso. Sus recursos representan lo que necesita el cliente, como el detalle de una solicitud o la disponibilidad de un ambiente. El [diseño del backend](BACKEND-DISENIO.md) describe qué módulo aplica las reglas y qué datos utiliza. [Historias de usuario](https://github.com/taller-web-sigesu/infra-uni/tree/269a0bcf7f90a68fdb16a80b177337dc6527b6fd/docs/requirements/user-stories/) · [Diseño de datos](https://github.com/taller-web-sigesu/infra-uni/blob/269a0bcf7f90a68fdb16a80b177337dc6527b6fd/DISENIO-BD.md)

## Convenciones del contrato

- API REST con prefijo `/api/v1`. Las tablas de abajo muestran rutas relativas a ese prefijo. Se conservan los nombres de recursos en inglés que usa la arquitectura del backend. Los campos de negocio se proponen en español y en `camelCase`.
- JSON para entradas y salidas, con CSV como formato inicial propuesto para descargas. `GET` consulta, `POST` crea recursos o inicia operaciones de sesión, `PUT` sustituye un recurso completo y `PATCH` modifica campos o subrecursos como `status`. Las altas de recursos devuelven `201 Created` con `Location` cuando existe una URL individual de consulta. Un `204 No Content` lleva el cuerpo vacío.
- Listados con `page` desde 0 y `size` de 20 por defecto y máximo 100. Respuesta con `items`, `page`, `size` y `totalElements`. Los filtros y órdenes admitidos se declaran por recurso. El servidor aplica primero el alcance del usuario y usa un orden estable con `id` como desempate.
- Se propone serializar los identificadores como cadenas decimales para evitar pérdida de precisión en JavaScript. `id` identifica el recurso en la URL y `codigo` es la referencia visible de negocio. Los instantes de creación, auditoría y SLA se devuelven en UTC. Las franjas se expresan mediante `fecha` (`YYYY-MM-DD`), `horaInicio` y `horaFin` (`HH:mm`) en la zona del campus, propuesta como `America/Lima`.
- Los errores siguen un formato común `application/problem+json`, con `type`, `title`, `status`, `detail`, `codigo`, `correlationId` y, cuando corresponda, `errores` por campo. `400` indica datos inválidos, `401` una credencial ausente o inválida, `403` una operación vedada al rol y `404` un recurso inexistente o fuera del alcance de visibilidad. `409` indica un conflicto con el estado vigente, como una franja ocupada, una transición inválida o una encuesta ya registrada. `429` indica que se alcanzó un límite de frecuencia. Los fallos internos tienen mensajes genéricos.
- Cada escritura valida campos, rol y acceso al recurso. Identidad del actor, fechas de registro, códigos y estados iniciales se determinan en el servidor. Ante un `409`, el cliente vuelve a consultar el recurso antes de intentar otro cambio.

## Acceso y sesión · HU-01 a HU-02

| Método y ruta | Acceso | Resultado |
| --- | --- | --- |
| `POST /auth/login` | Público | Recibe correo y contraseña. Devuelve `200` con el JWT de acceso, su vigencia, el usuario y el indicador `debeCambiarContrasena`. Emite la cookie de renovación. |
| `POST /auth/refresh` | Cookie de renovación válida | Rota el refresh token y devuelve `200` con un JWT de acceso nuevo y la cookie actualizada. La sesión conserva su alcance, incluido el acceso restringido del primer ingreso. |
| `POST /auth/logout` | Sesión actual, identificada por el token de acceso o la cookie | Revoca la sesión, borra la cookie y devuelve `204`. |
| `PATCH /auth/password` | Titular autenticado, incluida la sesión de primer ingreso | Recibe `contrasenaActual` y `contrasenaNueva`. Tras validar el cambio, revoca las sesiones previas y devuelve `200` con una nueva sesión y su cookie. |

El administrador crea las cuentas. Si `debeCambiarContrasena` es verdadero, la sesión permite cambiar la contraseña, renovarse o cerrarse. Una operación de negocio devuelve `403` con `CAMBIO_CONTRASENA_REQUERIDO`. La SPA envía el JWT mediante `Authorization: Bearer <token>` y lo conserva en memoria. El refresh token viaja en una cookie `HttpOnly`, `Secure` en despliegues HTTPS y con `SameSite=Lax` en el despliegue propuesto bajo el mismo sitio. Los errores de login son genéricos y los intentos tienen un límite de frecuencia. Tras cerrar sesión, el JWT anterior y el refresh token devuelven `401` cuando se intenta volver a usarlos. [ADR de sesión](https://github.com/taller-web-sigesu/infra-uni/blob/269a0bcf7f90a68fdb16a80b177337dc6527b6fd/docs/adr/ADR-0002-autenticacion-sesion.md)

## Registro y seguimiento de solicitudes · HU-03 a HU-06

| Método y ruta | Acceso | Resultado |
| --- | --- | --- |
| `GET /catalogs/categories` | Usuario autenticado | Lista categorías habilitadas para el registro y sus nombres visibles. |
| `POST /requests` | Estudiante | Recibe `categoriaId`, `ubicacion` y `descripcion`. Devuelve `201` con la solicitud en `NUEVO`, su código `SOL-AAAA-NNNNNN`, área, prioridad inicial y fecha. |
| `GET /requests` | Estudiante, técnico y supervisor | Devuelve solicitudes propias, asignadas al técnico o del área del supervisor, respectivamente. El resumen incluye código, categoría, estado, prioridad y fecha. |
| `GET /requests/{id}` | Propietario, técnico con asignación vigente o supervisor del área | Devuelve solicitante, categoría, área, ubicación, descripción, responsable actual, prioridad, estado, fechas e indicador de SLA. En estados terminales muestra el último responsable cuando exista. |
| `GET /requests/{id}/history` | Mismo alcance que el detalle | Devuelve cambios de estado en orden cronológico, con fecha, actor y motivo cuando corresponda. |
| `GET /requests/{id}/comments` y `POST /requests/{id}/comments` | Mismo alcance que el detalle | Consulta comentarios o añade un texto no vacío. El alta devuelve `201` con autor y fecha. |

## Asignación, prioridad y ciclo de vida · HU-07 a HU-11

| Método y ruta | Acceso | Resultado |
| --- | --- | --- |
| `GET /requests?estado=…&categoriaId=…&prioridadId=…` | Supervisor | Filtra la bandeja de su área. La bandeja del técnico usa la misma colección y ordena por nivel de prioridad descendente y fecha de registro ascendente. |
| `GET /requests/{id}/eligible-technicians` | Supervisor del área | Lista técnicos activos de esa área para asignar la solicitud. |
| `POST /requests/{id}/assignments` | Supervisor del área | Recibe `tecnicoId` y, opcionalmente, `prioridadId`. Devuelve `201`. La primera asignación pasa de `NUEVO` a `ASIGNADO`. La reasignación conserva el estado. |
| `PATCH /requests/{id}` | Supervisor del área | Actualiza únicamente `prioridadId` mientras la solicitud esté abierta. Devuelve el resumen actualizado. |
| `PATCH /requests/{id}/status` | Técnico asignado o supervisor del área | Recibe `estado` y `motivo` cuando la transición lo exige. Devuelve `200` con el estado actualizado. |

El técnico puede iniciar (`ASIGNADO → EN_PROCESO`) y resolver (`EN_PROCESO → RESUELTO`). El supervisor puede cerrar desde `RESUELTO` o rechazar desde `NUEVO`, `ASIGNADO` o `EN_PROCESO` con un motivo. Al llegar a `CERRADO` o `RECHAZADO` termina la asignación vigente y queda su historial. La solicitud sale de la bandeja del técnico y conserva la visibilidad del propietario y del supervisor del área. Un intento de reapertura devuelve `409` si el actor tiene acceso al recurso. Cambiar la prioridad de una solicitud abierta conserva su plazo de SLA original. [Reglas de datos](https://github.com/taller-web-sigesu/infra-uni/blob/269a0bcf7f90a68fdb16a80b177337dc6527b6fd/DISENIO-BD.md#7-reglas-de-integridad-y-negocio)

## Dashboard y reportes · HU-12 a HU-14

| Método y ruta | Acceso | Resultado |
| --- | --- | --- |
| `GET /dashboard/summary` | Supervisor de su área o administrador | Devuelve los indicadores, sus agrupaciones y los filtros aplicados. |
| `GET /dashboard/reports?tipo=solicitudes` o `?tipo=indicadores` | Mismo alcance que el dashboard | Descarga un CSV de gestión con los filtros aplicados y las columnas autorizadas para el reporte. |

Filtros compartidos `fechaDesde` y `fechaHasta` como instantes UTC obligatorios y `areaId` opcional. El rango de registro es `[desde, hasta)`. El supervisor usa su área y recibe `403` si pide otra. El administrador puede seleccionar un área o consultar todas. Los indicadores son registradas, pendientes, atendidas, por categoría, por prioridad, por responsable, horas promedio hasta la resolución, porcentaje de resueltas y vencidas con su desglose de próximas a vencer. El indicador por responsable usa el actual o el último registrado en una solicitud terminal. Mientras SLA esté pendiente de implementación, el indicador de vencimientos se muestra como no disponible. [Definiciones de los indicadores](https://github.com/taller-web-sigesu/infra-uni/blob/269a0bcf7f90a68fdb16a80b177337dc6527b6fd/DISENIO-BD.md#10-dashboard-los-9-indicadores)

## Usuarios y catálogos · HU-15 a HU-17

| Método y ruta | Acceso | Resultado |
| --- | --- | --- |
| `GET /admin/users`, `GET /admin/users/{id}`, `POST /admin/users` y `PATCH /admin/users/{id}` | Administrador | Consulta, alta con contraseña inicial, edición de perfil, asignación de un rol y baja lógica con `activo: false`. Área obligatoria para técnicos y supervisores. |
| `GET /admin/roles` | Administrador | Lista roles para la administración de cuentas. |
| `GET` y `POST /admin/catalogs/{catalogo}`, `PATCH /admin/catalogs/{catalogo}/{id}` | Administrador | Gestiona `areas`, `categories` y `priorities`. Las categorías requieren área y prioridad predeterminada. La baja es lógica. |
| `GET /admin/catalogs/request-statuses` y `PATCH /admin/catalogs/request-statuses/{id}` | Administrador | Consulta estados y actualiza sus nombres visibles. Los códigos y las reglas de transición son parte del ciclo de vida. |

## SLA, auditoría y satisfacción · HU-18 a HU-20

| Método y ruta | Acceso | Resultado |
| --- | --- | --- |
| `GET` y `POST /admin/sla-policies`, `PATCH /admin/sla-policies/{id}` | Administrador | Gestiona políticas por categoría o por prioridad con horas objetivo y de aviso. Los plazos ya asignados permanecen vigentes. |
| `GET /requests/{id}/audit-events` | Supervisor del área | Lista cambios auditados con campo, valor anterior, valor nuevo, actor y fecha. Es una consulta separada del historial visible al solicitante. |
| `POST /requests/{id}/survey` | Estudiante propietario | Registra una única encuesta con calificación de 1 a 5 y comentario opcional. Devuelve `201` cuando la solicitud está en `CERRADO`. |

## Reserva de ambientes · HU-21 a HU-25

| Método y ruta | Acceso | Resultado |
| --- | --- | --- |
| `GET /spaces` | Usuario autenticado | Lista espacios activos por tipo, pabellón, capacidad mínima y fecha. Incluye el horario del día consultado. |
| `GET /spaces/{id}/availability?fecha=…` | Usuario autenticado | Devuelve horario y franjas libres y ocupadas. La respuesta omite la identidad y el motivo de quienes reservaron. |
| `POST /reservations` | Usuario autenticado | Recibe `espacioId`, `fecha`, `horaInicio`, `horaFin`, `motivo` y, opcionalmente, `numAsistentes`. Devuelve `201` con código `RES-AAAA-NNNNNN` y estado `PENDIENTE`. |
| `GET /reservations` | Usuario autenticado | Lista reservas propias con código, espacio, fecha, horas y estado. Filtra por estado y rango de fechas. |
| `GET /reservations?vista=gestion&estado=PENDIENTE` | Supervisor del área del espacio o administrador | Devuelve la bandeja de aprobación. El alcance del supervisor se limita a espacios de su área y el del administrador abarca todos. |
| `GET /reservations/{id}` y `GET /reservations/{id}/history` | Propietario, supervisor del área del espacio o administrador | Devuelve detalle o historial. Incluye el motivo de rechazo cuando exista y el estado operativo derivado cuando corresponda. |
| `PATCH /reservations/{id}/status` | Según transición | Recibe `estado` y, para rechazo, `motivo`. Devuelve `200` con el estado actualizado. |
| `GET` y `POST /admin/spaces`, `PATCH /admin/spaces/{id}` | Administrador | Consulta, crea, edita y desactiva espacios. La baja impide nuevas reservas y conserva el historial. |
| `GET` y `POST /admin/space-types`, `PATCH /admin/space-types/{id}` | Administrador | Gestiona tipos de ambiente. |
| `GET` y `PUT /admin/spaces/{id}/schedule` | Administrador | Consulta o sustituye el horario semanal completo, con una franja válida por día de atención según el esquema propuesto. |
| `GET` y `PUT /admin/reservation-policy` | Administrador | Consulta o sustituye la política vigente de duración, anticipación y límite diario por usuario. |

Las reservas `PENDIENTE` y `CONFIRMADA` ocupan la franja. La disponibilidad es informativa hasta completar el alta. Un supervisor del área o un administrador puede confirmar o rechazar una reserva pendiente. Para espacios sin área decide el administrador. Está prohibida la autoaprobación y se revalida la franja al confirmar. Solo el propietario puede cancelar una reserva pendiente o confirmada antes del inicio, incluso cuando quien consulta una reserva ajena tiene permiso de gestión. Esta propuesta sigue HU-24 y requiere alinear la matriz de transiciones del diseño de datos.

Para el piloto se propone el calendario de `America/Lima`, con una franja semanal por día y espacio. Los feriados siguen el horario semanal hasta que se aprueben excepciones por fecha. Los valores de demostración son de 30 a 240 minutos por reserva, una hora mínima y 30 días calendario de anticipación máxima, y dos reservas pendientes o confirmadas por solicitante y fecha, contando todos los espacios. Los valores definitivos se validan con el área operativa. [Reglas de reservas](https://github.com/taller-web-sigesu/infra-uni/blob/269a0bcf7f90a68fdb16a80b177337dc6527b6fd/DISENIO-BD.md#78-reservas-de-ambientes)

## Criterios de aceptación propuestos

| Historia | Comprobación |
| --- | --- |
| HU-13 · Filtros | Una solicitud registrada exactamente en `fechaDesde` se incluye y otra en `fechaHasta` queda fuera. Un rango invertido o vacío devuelve `400` y el supervisor recibe `403` si consulta otra área. |
| HU-14 · Exportación | El CSV de solicitudes contiene todos los registros que cumplen los filtros y el alcance del actor. Si ninguno coincide, devuelve solo los encabezados. El CSV de indicadores coincide con el resumen para el mismo conjunto de datos. |
| HU-17 · Prioridades y estados | El administrador puede actualizar una prioridad y el nombre visible de un estado. El nombre nuevo aparece en las consultas y se mantienen el código y las transiciones del estado. Un intento de editar el código devuelve `400`. |
| HU-18 · SLA | Si hay una política vigente para categoría y otra para prioridad, se aplica la de categoría. Al alcanzar el aviso y luego superar el plazo, la solicitud abierta pasa a `POR_VENCER` y `VENCIDA`, respectivamente. Modificar una política o la prioridad conserva el plazo de una solicitud existente. |
| HU-19 · Auditoría | Al cambiar una prioridad, el supervisor del área ve el campo, el valor previo, el nuevo, el autor y la fecha. Otro supervisor recibe `404` al consultar el mismo recurso. |
| HU-20 · Encuesta | El propietario registra una calificación de 1 a 5 cuando la solicitud está cerrada. Una calificación inválida devuelve `400`. Una respuesta duplicada o un envío del propietario antes del cierre devuelve `409`. |

## Operación y cierre del contrato

`GET /api/v1/status` devuelve actualmente `{"service":"backend","status":"UP"}`. El estado de dependencias se consulta por separado mediante el Actuator configurado en `/actuator/health`.

Tras la aprobación, OpenAPI formalizará rutas, esquemas, filtros, formatos de respuesta y límites acordados con frontend. La matriz de seguridad detallará el alcance de cada rol y el acceso de primer ingreso. La arquitectura de referencia se alineará con el alta administrativa de cuentas y la ruta única de cambio de estado de reservas. [Plan de ingeniería](https://github.com/taller-web-sigesu/infra-uni/blob/269a0bcf7f90a68fdb16a80b177337dc6527b6fd/docs/superpowers/plans/2026-11-04-ingenieria-pendiente.md)
