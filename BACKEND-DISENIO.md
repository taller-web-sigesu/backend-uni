# Diseño del backend de Gestal

> Propuesta de alto nivel sujeta a aprobación del equipo. Hoy existe la base de Spring Boot con el controlador de estado, el datasource y Actuator. Los módulos, las reglas y las migraciones que siguen describen el diseño objetivo.

El backend es un monolito modular en Java y Spring Boot. React consume la [API REST](API-DISENIO.md), y el backend gestiona la persistencia en MySQL. Los módulos se comunican mediante servicios internos dentro de la misma aplicación. [Arquitectura existente](ARCHITECTURE.md) · [Diseño de datos](https://github.com/taller-web-sigesu/infra-uni/blob/269a0bcf7f90a68fdb16a80b177337dc6527b6fd/DISENIO-BD.md)

## Capas y responsabilidades

| Capa | Responsabilidad |
| --- | --- |
| Entrada HTTP | Recibir peticiones, validar su formato, obtener el actor autenticado y convertir resultados y errores a DTOs del contrato REST. |
| Aplicación | Ejecutar casos de uso, comprobar el alcance del actor, coordinar reglas y definir dónde empieza y termina cada transacción. |
| Dominio | Expresar invariantes y políticas como la elegibilidad de un técnico, las transiciones y la validez de una franja. |
| Persistencia | Implementar lecturas y escrituras con JPA o consultas específicas, controlar bloqueos y usar restricciones de MySQL como respaldo. |

Los módulos se agrupan por negocio bajo `pe.edu.universidad.servicios`. Cada uno expone los servicios que otros módulos necesitan y conserva sus entidades y repositorios como detalles internos. Las consultas compuestas de dashboard pueden leer proyecciones de MySQL con acceso de solo lectura. Las transacciones de escritura se coordinan desde un caso de uso, incluso cuando implican historial o auditoría.

## Acceso y sesión · `auth` · HU-01 a HU-02

Spring Security identifica al actor y aplica los permisos generales por rol. Los casos de uso verifican además propiedad, asignación o área. El login valida la cuenta activa y el hash de la contraseña, registra la sesión y emite las credenciales. `users` proporciona una consulta acotada de identidad, rol y área.

Según el ADR de sesión, el JWT de acceso dura 15 minutos y el refresh token opaco tiene vigencia absoluta de siete días. Se guarda el hash del refresh en `sesiones_usuario`. Cada JWT referencia su sesión. En operaciones protegidas, el servidor comprueba la vigencia y revocación de la sesión, además del estado, rol y área vigentes de la cuenta. El logout revoca la sesión actual y el siguiente acceso con esa credencial se deniega.

El esquema actual guarda un solo hash por sesión. Para detectar el reúso de un token ya rotado y revocar la sesión correcta, se propone conservar los hashes consumidos vinculados a la sesión hasta su vencimiento. El cliente debe serializar las renovaciones y el servidor debe aplicar la rotación de forma atómica. La revocación por reúso debe quedar confirmada incluso si la respuesta HTTP es un error. [ADR de autenticación](https://github.com/taller-web-sigesu/infra-uni/blob/269a0bcf7f90a68fdb16a80b177337dc6527b6fd/docs/adr/ADR-0002-autenticacion-sesion.md)

En el primer ingreso, el servidor lee `debe_cambiar_contrasena` y restringe la sesión al cambio de contraseña, renovación y logout. Al cambiarla, valida la contraseña actual y la nueva, guarda el nuevo hash, limpia el indicador, revoca las sesiones anteriores y registra la nueva sesión en una sola transacción. La emisión y la renovación se coordinan con los cambios de credenciales y la desactivación de la cuenta para impedir la emisión concurrente con datos obsoletos. El cliente recibe las nuevas credenciales tras confirmar los cambios. Para el despliegue se proponen dominios frontend y API del mismo sitio, HTTPS, CORS con orígenes explícitos cuando aplique y controles CSRF en las operaciones con cookies.

## Registro y seguimiento de solicitudes · `requests` · HU-03 a HU-06

El caso de uso de alta valida que la categoría, el área y la prioridad inicial estén habilitadas. Toma el área y la prioridad predeterminada de la categoría, congela los plazos de SLA aplicables y obtiene el código de la secuencia anual. Guarda la solicitud en `NUEVO`, su historial inicial y la auditoría de alta en una transacción.

Las consultas de detalle, historial y comentarios aplican el alcance del actor antes de devolver datos. Para las bandejas, el repositorio filtra por solicitante, área almacenada en la solicitud o responsable vigente. Los comentarios registran al autor autenticado y la fecha del servidor. Sus datos viven en `solicitudes`, `historial_estados`, `comentarios` y `secuencias_codigo_solicitud`.

## Asignación, prioridad y ciclo de vida · `management` · HU-07 a HU-11

`management` organiza las operaciones del supervisor y del técnico. Usa los casos de uso internos de `requests` para escribir sobre la solicitud y su historial, manteniendo un único punto de escritura. Consulta en `users` la elegibilidad del técnico y en `catalogs` los estados y reglas activas. Las transiciones se validan por origen, destino, rol y requisito de motivo en `transiciones_estado`, junto con el alcance concreto sobre la solicitud.

- La primera asignación crea la fila activa en `asignaciones`, actualiza el responsable y mueve la solicitud de `NUEVO` a `ASIGNADO`. La reasignación finaliza la asignación previa, crea otra y conserva el estado. El índice único de asignación activa y el control de versión de la solicitud protegen los cambios concurrentes.
- Los cambios de estado actualizan los datos derivados, como la fecha de resolución, la fecha de cierre o el motivo de rechazo. Al cerrar o rechazar, se finaliza la asignación activa si existe y se limpia el responsable vigente. Todo se guarda junto con el historial y la auditoría. Se conserva el último responsable en el historial para detalle y reportes. La bandeja y el acceso del técnico dependen de una asignación vigente.
- `CERRADO` y `RECHAZADO` son definitivos para este alcance. El servidor reevalúa el estado al escribir y traduce los conflictos concurrentes al contrato de la API. Los ajustes de prioridad en solicitudes abiertas conservan el SLA original.

## Dashboard y reportes · `dashboard` · HU-12 a HU-14

Una consulta de aplicación valida que el inicio del intervalo UTC sea anterior al fin y aplica el rango `[desde, hasta)` a la fecha de registro. Las agregaciones y la exportación reutilizan los mismos filtros y definiciones de indicadores. Para el supervisor, el área se obtiene de su identidad y un filtro por otra área se deniega. El administrador puede usar todas las áreas o seleccionar una.

Las proyecciones de lectura usan `solicitudes`, catálogos, `vw_solicitudes_detalle` y `vw_estado_sla`. Los estados actuales determinan pendientes y atendidas. El tiempo de atención se calcula desde el registro hasta la resolución. El indicador por responsable usa el actual y recupera el último de `asignaciones` para solicitudes terminales. Esta regla requiere ajustar la consulta de referencia de la sección 10.6 del diseño de datos. Los CSV se generan bajo demanda con columnas autorizadas y neutralizan los valores que una hoja de cálculo pueda interpretar como fórmulas. [Consultas de referencia](https://github.com/taller-web-sigesu/infra-uni/blob/269a0bcf7f90a68fdb16a80b177337dc6527b6fd/DISENIO-BD.md#10-dashboard-los-9-indicadores)

## Usuarios y catálogos · `users` y `catalogs` · HU-15 a HU-17

`users` centraliza el alta administrativa de cuentas y su asignación de rol y área. El primer administrador se aprovisiona mediante un procedimiento operativo controlado. El correo es único y los perfiles técnico y supervisor requieren un área activa. El alta guarda el hash seguro de la contraseña inicial y activa `debe_cambiar_contrasena`. Desactivar una cuenta revoca sus sesiones y conserva sus referencias históricas.

`catalogs` administra áreas, categorías, prioridades y nombres visibles de estados. Una categoría nueva requiere área y prioridad predeterminada activas. Los códigos de estado son estables y las bajas son lógicas. La configuración debe preservar las referencias y la posibilidad de completar solicitudes en curso. Las reglas iniciales de transición se siembran con Flyway y su edición desde la API queda sujeta a una historia que la habilite.

## SLA, auditoría y satisfacción · HU-18 a HU-20

- **SLA.** `sla` administra `politicas_sla` y permite como máximo una política vigente por categoría o prioridad para cada instante. Aplica la categoría primero y recurre a la prioridad si hace falta. Si ninguna aplica, la solicitud se registra sin SLA. `requests` guarda política, plazo y umbral de aviso al dar de alta la solicitud. `vw_estado_sla` deriva el estado respecto del reloj y el estado actual de la solicitud. Para la demo se reutilizan los objetivos y avisos de los datos semilla, en horas, para prioridad baja (72 y 12), media (48 y 8), alta (24 y 4), crítica (8 y 2) y soporte informático (24 y 4, con precedencia por categoría). Los responsables de área validarán los valores de operación.
- **Auditoría.** `audit` añade eventos a `auditoria_eventos` en la misma transacción de la operación de negocio. Registra actor, momento, recurso y cambios relevantes con valores anteriores y nuevos aptos para auditoría. El supervisor consulta los eventos de solicitudes de su área. El historial de estados es la línea de tiempo de negocio y tiene su propio acceso. Se excluyen credenciales y datos sensibles innecesarios.
- **Satisfacción.** `requests` recibe una calificación de 1 a 5 del solicitante de una solicitud cerrada. La validación de propiedad y estado se combina con la restricción única de `encuestas_satisfaccion` para impedir dos respuestas concurrentes.

## Reserva de ambientes · `reservations` · HU-21 a HU-25

El módulo tiene casos de uso separados para disponibilidad, alta, decisión, cancelación y administración de espacios. Usa `espacios`, `tipos_espacio`, `horarios_espacio` y `politicas_reserva` como configuración. Persiste el ciclo de vida en `reservas` e `historial_estados_reserva`, con códigos de `secuencias_codigo_reserva` y eventos en `auditoria_eventos`.

1. **Disponibilidad.** Cruza el horario activo del día con las franjas de reservas pendientes o confirmadas. Una fecha sin horario queda cerrada. Para el piloto, se usa una franja por espacio y día de la semana y se aplica ese horario también en feriados. Las excepciones por fecha y los horarios partidos quedan como ampliaciones. Las fechas y horas de reserva se guardan como calendario local de `America/Lima` y se convierten a instantes para comparar con el reloj. La vista `vw_reservas_operativas` requiere alinearse con esa conversión.
2. **Alta.** Comprueba actividad del espacio, que la franja todavía no haya comenzado, horario, duración, anticipación, capacidad y límite diario por usuario. Se propone serializar las altas del mismo solicitante con un bloqueo sobre su fila, previo al bloqueo del espacio, para evitar que dos altas en espacios distintos excedan el límite. Con el espacio bloqueado, consulta las franjas activas mediante una lectura que vea los cambios confirmados antes de validar. Dos franjas contiguas pueden compartir el instante de fin e inicio. Crea la reserva en `PENDIENTE`, con historial y auditoría en la misma transacción.
3. **Decisión y cancelación.** Para confirmar, bloquea el espacio, valida estado y permisos, excluye la propia reserva de la consulta de solapamiento y actualiza la decisión. El supervisor actúa sobre su área y el administrador puede decidir sobre cualquier espacio. Se impide la autoaprobación. Rechazar requiere un motivo. Solo el propietario cancela una reserva pendiente o confirmada antes del inicio. Esta regla requiere alinear la matriz de la sección 7.8 del diseño de datos, que también incluye al administrador. El control de versión evita que dos decisiones concurrentes sobrescriban el estado y cada transición confirmada deja historial y auditoría. El estado operativo de las reservas confirmadas se deriva del horario.
4. **Administración.** La baja lógica del espacio impide nuevas altas y conserva reservas existentes. Los cambios de política se aplican a nuevas reservas. En la demo se reutiliza la política semilla de 30 a 240 minutos, mínimo una hora antes del inicio, máximo 30 días calendario desde la fecha local actual y hasta dos reservas pendientes o confirmadas por usuario y fecha entre todos los espacios. La aprobación es obligatoria en EP-07 y `requiere_aprobacion` se mantiene en verdadero para este alcance.

El bloqueo del espacio serializa la validación de solapamientos. El índice actual `uk_reservas_franja_exacta` también compara filas en estados terminales. Una reserva posterior con la misma hora de inicio podría quedar impedida de pasar a `CANCELADA` si ya existe otra cancelada con esa clave. Se propone una clave única por espacio, fecha e inicio que solo se aplica a reservas pendientes o confirmadas. El bloqueo sigue cubriendo los cruces parciales. [Reglas e índices de reservas](https://github.com/taller-web-sigesu/infra-uni/blob/269a0bcf7f90a68fdb16a80b177337dc6527b6fd/DISENIO-BD.md#78-reservas-de-ambientes)

## Aplicación de SOLID

| Principio | Decisión de diseño |
| --- | --- |
| Responsabilidad única | El controlador adapta HTTP. El caso de uso coordina. La política decide. La persistencia guarda y consulta. Asignar una solicitud y construir un reporte tienen servicios separados. |
| Abierto/cerrado | Las transiciones permitidas y los plazos de SLA se ajustan mediante configuración validada. Una transición sigue obligada a cumplir las invariantes de asignación, alcance y cierre. |
| Sustitución de Liskov | Los adaptadores de una misma interfaz mantienen el alcance autorizado, los órdenes pactados y los resultados de ausencia o conflicto. Los dobles de prueba respetan ese contrato. |
| Segregación de interfaces | El dashboard consume consultas acotadas. La asignación consume una consulta de técnicos elegibles. Cada consumidor depende solo de las operaciones que usa. |
| Inversión de dependencias | Los casos de uso dependen de contratos para persistir, obtener el tiempo o emitir tokens. Spring inyecta las implementaciones y las políticas pueden probarse con un reloj controlado. |

## Persistencia, operación y criterios de verificación

- **Migraciones.** Flyway versiona el esquema y los catálogos semilla en `src/main/resources/db/migration/`. JPA conserva `ddl-auto: none`. Los cambios de datos se prueban contra MySQL con claves foráneas, unicidad, restricciones y bloqueos reales. Los datos de demostración se mantienen separados del esquema y de los catálogos base.
- **Controles compartidos.** `shared` centraliza errores HTTP, correlación, configuración y observabilidad. CORS y CSRF se ajustan al flujo de cookies, se limita el login, se gestionan secretos por entorno y se protege Actuator. Se registra latencia, errores y salud de dependencias con exclusión de credenciales y cuerpos sensibles. [ADR de observabilidad](https://github.com/taller-web-sigesu/infra-uni/blob/269a0bcf7f90a68fdb16a80b177337dc6527b6fd/docs/adr/ADR-0003-observabilidad-retencion.md)
- **Verificación al implementar.** Cubrir los criterios del diseño de API, las transiciones y las políticas. Con API y MySQL, comprobar que el primer ingreso quede restringido hasta cambiar la contraseña, que los tokens anteriores pierdan acceso tras logout o cambio de contraseña y que la reutilización de un refresh rotado revoque la sesión. Verificar la finalización atómica de la asignación, historial y auditoría, y el cómputo del último responsable. Para reservas, probar solapamientos, franjas contiguas, reservar y volver a cancelar una franja liberada, y el límite diario concurrente entre espacios. [Requisitos no funcionales](https://github.com/taller-web-sigesu/infra-uni/blob/269a0bcf7f90a68fdb16a80b177337dc6527b6fd/docs/requirements/Gestal_Requisitos_No_Funcionales.md)

## Decisiones para revisión del equipo

| Tema | Propuesta o decisión pendiente |
| --- | --- |
| Sesión | Aprobar el alta administrativa, cambio obligatorio en primer ingreso y revocación inmediata. Alinear el ADR de sesión y el esquema para conservar hashes de renovación consumidos. |
| Solicitudes | Aprobar los estados terminales definitivos, el fin de la asignación al cerrar o rechazar y la conservación del SLA original. Alinear la proyección por responsable del diseño de datos. |
| Reservas | Aprobar cancelación solo por el propietario, calendario de Lima y una franja semanal por día. Alinear permisos, vista operativa e índice de franjas activas en el diseño de datos. |
| Datos y auditoría | Usar catálogos ficticios y políticas semilla para la demo. Validar catálogos y valores con los responsables antes del uso real. Conservar la auditoría durante el piloto y acordar su retención y eliminación con el responsable de datos antes de producción. |
| Contrato REST | Revisar [API-DISENIO.md](API-DISENIO.md) y los criterios de aceptación para seis historias con frontend. Trasladar lo aprobado a OpenAPI y a la matriz de permisos. |
| Operación | Confirmar los ADR de observabilidad, despliegue y CI/CD. Elegir el proveedor de MySQL tras verificar conexión privada o restringida, TLS y una restauración en staging. Acordar dominios antes de probar cookies y CORS en ese entorno. |
