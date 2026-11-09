# Diseño del backend de Gestal

> Propuesta de alto nivel sujeta a aprobación del equipo. Hoy existe la base de Spring Boot con el controlador de estado, el datasource y Actuator. Los módulos, las reglas y las migraciones que siguen describen el diseño objetivo.

El backend es un monolito modular en Java y Spring Boot. React consume la [API REST](API-DISENIO.md), y el backend gestiona la persistencia en MySQL. Los módulos se comunican mediante servicios internos dentro de la misma aplicación. [Arquitectura existente](../ARCHITECTURE.md) · [Diseño de datos](https://github.com/taller-web-sigesu/infra-uni/blob/269a0bcf7f90a68fdb16a80b177337dc6527b6fd/DISENIO-BD.md)

## Capas y responsabilidades

| Capa | Responsabilidad |
| --- | --- |
| Entrada HTTP | Recibir peticiones, validar su formato, obtener el actor autenticado y convertir resultados y errores a DTOs del contrato REST. |
| Aplicación | Ejecutar casos de uso, comprobar el alcance del actor, coordinar reglas y definir dónde empieza y termina cada transacción. |
| Dominio | Expresar invariantes y políticas como la elegibilidad de un técnico, las transiciones y la validez de una franja. |
| Persistencia | Implementar lecturas y escrituras con JPA o consultas específicas, controlar bloqueos y usar restricciones de MySQL como respaldo. |

Los módulos se agrupan por negocio bajo `pe.edu.universidad.servicios`. Cada uno expone los servicios que otros módulos necesitan y conserva sus entidades y repositorios como detalles internos. Las consultas compuestas de dashboard pueden leer proyecciones de MySQL con acceso de solo lectura. Las transacciones de escritura se coordinan desde un caso de uso, incluso cuando implican historial o auditoría.

## Organización de paquetes y archivos

El punto de partida es `src/main/java/pe/edu/universidad/servicios/`. Cada árbol es una propuesta de nombres y responsabilidades. Los controladores registran las rutas mediante anotaciones de Spring MVC. Los DTOs son los esquemas HTTP de entrada y salida. El esquema MySQL se versiona en `src/main/resources/db/migration/` con Flyway.

| Carpeta | Contenido |
| --- | --- |
| `controller/` | Controladores HTTP. Cada método recibe parámetros o DTOs, aplica el contrato HTTP y delega el caso de uso. |
| `dto/` | Cuerpos, filtros y vistas de respuesta. Cada nombre de DTO identifica la operación o recurso que modela. |
| `service/` | Casos de uso, consultas autorizadas y coordinación de transacciones. |
| `domain/` | Entidades, estados, reglas y objetos de valor del módulo. |
| `repository/` | Repositorios JPA, lecturas proyectadas y operaciones de secuencias o bloqueo. |
| `security/`, `config/`, `web/`, `report/` | Componentes específicos de autenticación, configuración transversal, soporte HTTP o generación CSV. |

Los componentes de un módulo usan servicios de otros módulos mediante llamadas de Java. La tabla de rutas indica el punto de entrada HTTP que consume React. Las vistas de lectura de MySQL y Actuator son dependencias de plataforma. Los repositorios se mantienen internos al módulo propietario.

### `status`

El controlador existente conserva su ruta. Actuator aporta el endpoint de salud mediante configuración de Spring Boot.

```text
status/
└── StatusController.java (existente)
```

### `auth`

Usa `users/service/UserIdentityService.java` para verificar credenciales, aplicar el cambio de contraseña y consultar el rol y área vigentes. `SessionRevocationService` ejecuta el contrato compartido de revocación. `SessionService` valida, rota y revoca credenciales. `PasswordChangeService` coordina cambio, revocación y alta de sesión en una transacción.

```text
auth/
├── controller/
│   └── AuthController.java
├── dto/
│   ├── LoginRequest.java
│   ├── ChangePasswordRequest.java
│   ├── SessionResponse.java
│   └── SessionUserResponse.java
├── service/
│   ├── SessionService.java
│   ├── PasswordChangeService.java
│   └── SessionRevocationService.java
├── domain/
│   ├── UserSession.java
│   └── ConsumedRefreshToken.java
├── repository/
│   ├── UserSessionRepository.java
│   └── ConsumedRefreshTokenRepository.java
└── security/
    ├── SessionAuthenticationFilter.java
    ├── AccessTokenService.java
    └── RefreshCookieFactory.java
```

### `requests`

Consulta catálogos y SLA al registrar. Usa `audit/service/AuditEventService.java` para escrituras y `audit/service/AuditQueryService.java` para consultas ya autorizadas. `RequestAccessService` valida propiedad, área o asignación. `RequestWorkflowService` centraliza los cambios de prioridad, asignación y estado que ejecuta `management`.

```text
requests/
├── controller/
│   ├── RequestController.java
│   ├── RequestHistoryController.java
│   ├── RequestCommentController.java
│   ├── RequestAuditController.java
│   └── RequestSurveyController.java
├── dto/
│   ├── CreateRequestRequest.java
│   ├── RequestFilters.java
│   ├── RequestSummaryResponse.java
│   ├── RequestDetailResponse.java
│   ├── RequestHistoryResponse.java
│   ├── CreateCommentRequest.java
│   ├── CommentResponse.java
│   ├── SubmitSurveyRequest.java
│   ├── SurveyResponse.java
│   └── RequestAuditEventResponse.java
├── service/
│   ├── RequestRegistrationService.java
│   ├── RequestQueryService.java
│   ├── RequestHistoryService.java
│   ├── RequestCommentService.java
│   ├── RequestAccessService.java
│   ├── RequestWorkflowService.java
│   └── RequestSurveyService.java
├── domain/
│   ├── ServiceRequest.java
│   ├── RequestStateChange.java
│   ├── RequestComment.java
│   ├── RequestAssignment.java
│   └── SatisfactionSurvey.java
└── repository/
    ├── ServiceRequestRepository.java
    ├── RequestReadRepository.java
    ├── RequestHistoryRepository.java
    ├── RequestCommentRepository.java
    ├── RequestAssignmentRepository.java
    ├── SatisfactionSurveyRepository.java
    └── RequestCodeRepository.java
```

### `management`

Consulta técnicos elegibles en `users/service/TechnicianDirectoryService.java` y transiciones en `catalogs/service/RequestStatusCatalogService.java`. Delega las escrituras a `requests/service/RequestWorkflowService.java`. Reutiliza `RequestSummaryResponse` y mantiene una sola propiedad de los datos de solicitudes.

```text
management/
├── controller/
│   └── RequestManagementController.java
├── dto/
│   ├── EligibleTechnicianResponse.java
│   ├── AssignTechnicianRequest.java
│   ├── AssignmentResponse.java
│   ├── ChangePriorityRequest.java
│   └── ChangeRequestStatusRequest.java
├── service/
│   └── RequestManagementService.java
└── domain/
    └── RequestTransitionPolicy.java
```

### `dashboard`

Consulta proyecciones de solicitudes, catálogos y asignaciones con `DashboardReadRepository`, que también usa las vistas de detalle y SLA. `DashboardScope` resuelve área e intervalo para resumen y CSV. `DashboardCsvWriter` escribe encabezados, filas y valores seguros para hojas de cálculo.

```text
dashboard/
├── controller/
│   └── DashboardController.java
├── dto/
│   ├── DashboardFilters.java
│   ├── DashboardSummaryResponse.java
│   ├── DashboardGroupResponse.java
│   └── DashboardSlaResponse.java
├── service/
│   ├── DashboardService.java
│   └── DashboardReportService.java
├── domain/
│   └── DashboardScope.java
├── repository/
│   └── DashboardReadRepository.java
└── report/
    └── DashboardCsvWriter.java
```

### `users`

Consulta el área en `catalogs/service/AreaCatalogService.java`. `UserIdentityService` permite a `auth` consultar cuentas y cambiar su hash. `UserAdminService` invoca `SessionRevocation` al desactivar. `TechnicianDirectoryService` devuelve candidatos activos del área a `management`.

```text
users/
├── controller/
│   ├── AdminUserController.java
│   └── AdminRoleController.java
├── dto/
│   ├── CreateUserRequest.java
│   ├── UpdateUserRequest.java
│   ├── UserResponse.java
│   └── RoleResponse.java
├── service/
│   ├── UserAdminService.java
│   ├── UserIdentityService.java
│   ├── TechnicianDirectoryService.java
│   └── RoleQueryService.java
├── domain/
│   ├── User.java
│   └── Role.java
└── repository/
    ├── UserRepository.java
    └── RoleRepository.java
```

### `catalogs`

Publica lecturas internas de áreas, categorías, prioridades y transiciones para `users`, `requests`, `management` y `reservations`. Cada servicio administra su catálogo y valida las referencias con consultas locales o con el contrato interno pertinente.

```text
catalogs/
├── controller/
│   ├── CategoryCatalogController.java
│   ├── AdminAreaController.java
│   ├── AdminCategoryController.java
│   ├── AdminPriorityController.java
│   └── AdminRequestStatusController.java
├── dto/
│   ├── CreateAreaRequest.java
│   ├── UpdateAreaRequest.java
│   ├── AreaResponse.java
│   ├── CreateCategoryRequest.java
│   ├── UpdateCategoryRequest.java
│   ├── CategoryResponse.java
│   ├── AvailableCategoryResponse.java
│   ├── CreatePriorityRequest.java
│   ├── UpdatePriorityRequest.java
│   ├── PriorityResponse.java
│   ├── RenameRequestStatusRequest.java
│   └── RequestStatusResponse.java
├── service/
│   ├── AreaCatalogService.java
│   ├── CategoryCatalogService.java
│   ├── PriorityCatalogService.java
│   └── RequestStatusCatalogService.java
├── domain/
│   ├── Area.java
│   ├── Category.java
│   ├── Priority.java
│   ├── RequestStatus.java
│   └── RequestStatusTransition.java
└── repository/
    ├── AreaRepository.java
    ├── CategoryRepository.java
    ├── PriorityRepository.java
    ├── RequestStatusRepository.java
    └── RequestStatusTransitionRepository.java
```

### `sla`

`SlaResolutionService` resuelve la política aplicable y los plazos para `requests` al registrar una solicitud. `SlaPolicyService` usa el catálogo para validar alcance y evita vigencias incompatibles.

```text
sla/
├── controller/
│   └── AdminSlaPolicyController.java
├── dto/
│   ├── CreateSlaPolicyRequest.java
│   ├── UpdateSlaPolicyRequest.java
│   └── SlaPolicyResponse.java
├── service/
│   ├── SlaPolicyService.java
│   └── SlaResolutionService.java
├── domain/
│   ├── SlaPolicy.java
│   └── SlaDeadline.java
└── repository/
    └── SlaPolicyRepository.java
```

### `audit`

`AuditEventService` incorpora eventos a la transacción del escritor. `AuditQueryService` consulta eventos del recurso ya autorizado por `requests`. El módulo carece de rutas propias y entrega resultados de dominio para que el controlador dueño aplique su DTO.

```text
audit/
├── service/
│   ├── AuditEventService.java
│   └── AuditQueryService.java
├── domain/
│   └── AuditEvent.java
└── repository/
    └── AuditEventRepository.java
```

### `reservations`

Usa `AreaCatalogService` para referencias de áreas, `UserIdentityService` para la cuenta y el bloqueo del solicitante, y `AuditEventService` para registrar cambios. `ReservationService` coordina alta y `ReservationDecisionService` gobierna confirmación, rechazo y cancelación. Las escrituras de reservas y la disponibilidad comparten las reglas de franja.

```text
reservations/
├── controller/
│   ├── SpaceController.java
│   ├── ReservationController.java
│   ├── AdminSpaceController.java
│   ├── AdminSpaceTypeController.java
│   ├── AdminSpaceScheduleController.java
│   └── AdminReservationPolicyController.java
├── dto/
│   ├── SpaceFilters.java
│   ├── SpaceSummaryResponse.java
│   ├── SpaceAvailabilityResponse.java
│   ├── TimeSlotResponse.java
│   ├── CreateReservationRequest.java
│   ├── ReservationFilters.java
│   ├── ReservationSummaryResponse.java
│   ├── ReservationDetailResponse.java
│   ├── ReservationHistoryResponse.java
│   ├── ChangeReservationStatusRequest.java
│   ├── CreateSpaceRequest.java
│   ├── UpdateSpaceRequest.java
│   ├── SpaceResponse.java
│   ├── CreateSpaceTypeRequest.java
│   ├── UpdateSpaceTypeRequest.java
│   ├── SpaceTypeResponse.java
│   ├── ReplaceSpaceScheduleRequest.java
│   ├── ScheduleSlot.java
│   ├── SpaceScheduleResponse.java
│   ├── ReplaceReservationPolicyRequest.java
│   └── ReservationPolicyResponse.java
├── service/
│   ├── SpaceQueryService.java
│   ├── ReservationQueryService.java
│   ├── ReservationService.java
│   ├── ReservationDecisionService.java
│   ├── SpaceAdminService.java
│   ├── SpaceTypeAdminService.java
│   ├── SpaceScheduleService.java
│   └── ReservationPolicyService.java
├── domain/
│   ├── Space.java
│   ├── SpaceType.java
│   ├── SpaceSchedule.java
│   ├── ReservationPolicy.java
│   ├── Reservation.java
│   ├── ReservationStatus.java
│   ├── ReservationStateChange.java
│   ├── ReservationAccessPolicy.java
│   └── ReservationTimePolicy.java
└── repository/
    ├── SpaceRepository.java
    ├── SpaceTypeRepository.java
    ├── SpaceScheduleRepository.java
    ├── ReservationPolicyRepository.java
    ├── ReservationRepository.java
    ├── ReservationStatusRepository.java
    ├── ReservationHistoryRepository.java
    └── ReservationCodeRepository.java
```

### `shared`

Comparte configuración, actor autenticado, revocación, errores, paginación y correlación. No contiene reglas de solicitudes o reservas.

```text
shared/
├── dto/
│   ├── PageResponse.java
│   ├── ProblemResponse.java
│   └── FieldErrorResponse.java
├── config/
│   ├── SecurityConfiguration.java
│   ├── WebConfiguration.java
│   └── ClockConfiguration.java
├── security/
│   ├── AuthenticatedActor.java
│   └── SessionRevocation.java
└── web/
    ├── ApiExceptionHandler.java
    └── CorrelationIdFilter.java
```

## Rutas, controladores y servicios

Las rutas de negocio se muestran sin `/api/v1`. Cada enlace lleva a sus parámetros, seguridad y ejemplos. El nombre después del controlador identifica el método previsto.

### Acceso y sesión

| Operación | Módulo y controlador | Servicio o caso de uso |
| --- | --- | --- |
| [`POST /auth/login`](api/autenticacion.md#post-authlogin) | `auth/AuthController.login` | `SessionService` |
| [`POST /auth/refresh`](api/autenticacion.md#post-authrefresh) | `auth/AuthController.refresh` | `SessionService` |
| [`POST /auth/logout`](api/autenticacion.md#post-authlogout) | `auth/AuthController.logout` | `SessionService` |
| [`PATCH /auth/password`](api/autenticacion.md#patch-authpassword) | `auth/AuthController.changePassword` | `PasswordChangeService` |

### Registro y seguimiento de solicitudes

| Operación | Módulo y controlador | Servicio o caso de uso |
| --- | --- | --- |
| [`POST /requests`](api/solicitudes.md#post-requests) | `requests/RequestController.create` | `RequestRegistrationService` |
| [`GET /requests`](api/solicitudes.md#get-requests) | `requests/RequestController.list` | `RequestQueryService` |
| [`GET /requests/{id}`](api/solicitudes.md#get-requestsid) | `requests/RequestController.detail` | `RequestQueryService` |
| [`GET /requests/{id}/history`](api/solicitudes.md#get-requestsidhistory) | `requests/RequestHistoryController.list` | `RequestHistoryService` |
| [`GET /requests/{id}/comments`](api/solicitudes.md#get-requestsidcomments) | `requests/RequestCommentController.list` | `RequestCommentService` |
| [`POST /requests/{id}/comments`](api/solicitudes.md#post-requestsidcomments) | `requests/RequestCommentController.create` | `RequestCommentService` |

### Asignación, prioridad y ciclo de vida

| Operación | Módulo y controlador | Servicio o caso de uso |
| --- | --- | --- |
| [`GET /requests/{id}/eligible-technicians`](api/atencion.md#get-requestsideligible-technicians) | `management/RequestManagementController.eligibleTechnicians` | `RequestManagementService` |
| [`POST /requests/{id}/assignments`](api/atencion.md#post-requestsidassignments) | `management/RequestManagementController.assign` | `RequestManagementService` |
| [`PATCH /requests/{id}`](api/atencion.md#patch-requestsid) | `management/RequestManagementController.changePriority` | `RequestManagementService` |
| [`PATCH /requests/{id}/status`](api/atencion.md#patch-requestsidstatus) | `management/RequestManagementController.changeStatus` | `RequestManagementService` |

### Dashboard y reportes

| Operación | Módulo y controlador | Servicio o caso de uso |
| --- | --- | --- |
| [`GET /dashboard/summary`](api/dashboard.md#get-dashboardsummary) | `dashboard/DashboardController.summary` | `DashboardService` |
| [`GET /dashboard/reports`](api/dashboard.md#get-dashboardreports) | `dashboard/DashboardController.report` | `DashboardReportService` |

### Usuarios y roles

| Operación | Módulo y controlador | Servicio o caso de uso |
| --- | --- | --- |
| [`GET /admin/users`](api/usuarios.md#get-adminusers) | `users/AdminUserController.list` | `UserAdminService` |
| [`GET /admin/users/{id}`](api/usuarios.md#get-adminusersid) | `users/AdminUserController.detail` | `UserAdminService` |
| [`POST /admin/users`](api/usuarios.md#post-adminusers) | `users/AdminUserController.create` | `UserAdminService` |
| [`PATCH /admin/users/{id}`](api/usuarios.md#patch-adminusersid) | `users/AdminUserController.update` | `UserAdminService` |
| [`GET /admin/roles`](api/usuarios.md#get-adminroles) | `users/AdminRoleController.list` | `RoleQueryService` |

### Catálogos del servicio

| Operación | Módulo y controlador | Servicio o caso de uso |
| --- | --- | --- |
| [`GET /catalogs/categories`](api/catalogos.md#get-catalogscategories) | `catalogs/CategoryCatalogController.listAvailable` | `CategoryCatalogService` |
| [`GET /admin/catalogs/areas`](api/catalogos.md#get-admincatalogsareas) | `catalogs/AdminAreaController.list` | `AreaCatalogService` |
| [`POST /admin/catalogs/areas`](api/catalogos.md#post-admincatalogsareas) | `catalogs/AdminAreaController.create` | `AreaCatalogService` |
| [`PATCH /admin/catalogs/areas/{id}`](api/catalogos.md#patch-admincatalogsareasid) | `catalogs/AdminAreaController.update` | `AreaCatalogService` |
| [`GET /admin/catalogs/categories`](api/catalogos.md#get-admincatalogscategories) | `catalogs/AdminCategoryController.list` | `CategoryCatalogService` |
| [`POST /admin/catalogs/categories`](api/catalogos.md#post-admincatalogscategories) | `catalogs/AdminCategoryController.create` | `CategoryCatalogService` |
| [`PATCH /admin/catalogs/categories/{id}`](api/catalogos.md#patch-admincatalogscategoriesid) | `catalogs/AdminCategoryController.update` | `CategoryCatalogService` |
| [`GET /admin/catalogs/priorities`](api/catalogos.md#get-admincatalogspriorities) | `catalogs/AdminPriorityController.list` | `PriorityCatalogService` |
| [`POST /admin/catalogs/priorities`](api/catalogos.md#post-admincatalogspriorities) | `catalogs/AdminPriorityController.create` | `PriorityCatalogService` |
| [`PATCH /admin/catalogs/priorities/{id}`](api/catalogos.md#patch-admincatalogsprioritiesid) | `catalogs/AdminPriorityController.update` | `PriorityCatalogService` |
| [`GET /admin/catalogs/request-statuses`](api/catalogos.md#get-admincatalogsrequest-statuses) | `catalogs/AdminRequestStatusController.list` | `RequestStatusCatalogService` |
| [`PATCH /admin/catalogs/request-statuses/{id}`](api/catalogos.md#patch-admincatalogsrequest-statusesid) | `catalogs/AdminRequestStatusController.rename` | `RequestStatusCatalogService` |

### SLA, auditoría y satisfacción

| Operación | Módulo y controlador | Servicio o caso de uso |
| --- | --- | --- |
| [`GET /admin/sla-policies`](api/calidad-servicio.md#get-adminsla-policies) | `sla/AdminSlaPolicyController.list` | `SlaPolicyService` |
| [`POST /admin/sla-policies`](api/calidad-servicio.md#post-adminsla-policies) | `sla/AdminSlaPolicyController.create` | `SlaPolicyService` |
| [`PATCH /admin/sla-policies/{id}`](api/calidad-servicio.md#patch-adminsla-policiesid) | `sla/AdminSlaPolicyController.update` | `SlaPolicyService` |
| [`GET /requests/{id}/audit-events`](api/calidad-servicio.md#get-requestsidaudit-events) | `requests/RequestAuditController.list` | `RequestAccessService + AuditQueryService` |
| [`POST /requests/{id}/survey`](api/calidad-servicio.md#post-requestsidsurvey) | `requests/RequestSurveyController.create` | `RequestSurveyService` |

### Reserva de ambientes

| Operación | Módulo y controlador | Servicio o caso de uso |
| --- | --- | --- |
| [`GET /spaces`](api/reservas.md#get-spaces) | `reservations/SpaceController.list` | `SpaceQueryService` |
| [`GET /spaces/{id}/availability`](api/reservas.md#get-spacesidavailability) | `reservations/SpaceController.availability` | `SpaceQueryService` |
| [`POST /reservations`](api/reservas.md#post-reservations) | `reservations/ReservationController.create` | `ReservationService` |
| [`GET /reservations`](api/reservas.md#get-reservations) | `reservations/ReservationController.list` | `ReservationQueryService` |
| [`GET /reservations/{id}`](api/reservas.md#get-reservationsid) | `reservations/ReservationController.detail` | `ReservationQueryService` |
| [`GET /reservations/{id}/history`](api/reservas.md#get-reservationsidhistory) | `reservations/ReservationController.history` | `ReservationQueryService` |
| [`PATCH /reservations/{id}/status`](api/reservas.md#patch-reservationsidstatus) | `reservations/ReservationController.changeStatus` | `ReservationDecisionService` |
| [`GET /admin/spaces`](api/reservas.md#get-adminspaces) | `reservations/AdminSpaceController.list` | `SpaceAdminService` |
| [`POST /admin/spaces`](api/reservas.md#post-adminspaces) | `reservations/AdminSpaceController.create` | `SpaceAdminService` |
| [`PATCH /admin/spaces/{id}`](api/reservas.md#patch-adminspacesid) | `reservations/AdminSpaceController.update` | `SpaceAdminService` |
| [`GET /admin/space-types`](api/reservas.md#get-adminspace-types) | `reservations/AdminSpaceTypeController.list` | `SpaceTypeAdminService` |
| [`POST /admin/space-types`](api/reservas.md#post-adminspace-types) | `reservations/AdminSpaceTypeController.create` | `SpaceTypeAdminService` |
| [`PATCH /admin/space-types/{id}`](api/reservas.md#patch-adminspace-typesid) | `reservations/AdminSpaceTypeController.update` | `SpaceTypeAdminService` |
| [`GET /admin/spaces/{id}/schedule`](api/reservas.md#get-adminspacesidschedule) | `reservations/AdminSpaceScheduleController.get` | `SpaceScheduleService` |
| [`PUT /admin/spaces/{id}/schedule`](api/reservas.md#put-adminspacesidschedule) | `reservations/AdminSpaceScheduleController.replace` | `SpaceScheduleService` |
| [`GET /admin/reservation-policy`](api/reservas.md#get-adminreservation-policy) | `reservations/AdminReservationPolicyController.get` | `ReservationPolicyService` |
| [`PUT /admin/reservation-policy`](api/reservas.md#put-adminreservation-policy) | `reservations/AdminReservationPolicyController.replace` | `ReservationPolicyService` |

### Operación y salud

| Operación | Módulo y controlador | Servicio o caso de uso |
| --- | --- | --- |
| [`GET /status`](api/operacion.md#get-status) | `status/StatusController.status` | `Indicador fijo` |
| [`GET /actuator/health`](api/operacion.md#get-actuatorhealth) | Spring Boot Actuator | `Actuator / health` |

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
