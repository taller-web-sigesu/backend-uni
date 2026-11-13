# Seguridad y matriz de permisos del backend

> Estado: implementado en `feature/implementacion-backend` y verificado con las pruebas de integración de `src/test`. Fuente de alcance: HU-01 a HU-25, [diseño REST](API-DISENIO.md) y [ADR de sesión](https://github.com/taller-web-sigesu/infra-uni/blob/main/docs/adr/ADR-0002-autenticacion-sesion.md).

## Sesión

| Elemento | Implementación |
| --- | --- |
| Credencial de negocio | `Authorization: Bearer <JWT>` HS256 de 15 minutos con `sub` (usuario) y `sid` (sesión). Secreto en `APP_JWT_SECRET` (mínimo 32 bytes). |
| Vigencia por petición | `SessionAuthenticationFilter` lee la sesión y la cuenta en cada petición: sesión no revocada y sin vencer, cuenta `activo = 1` y `estado_cuenta = 'ACTIVA'`. Rol y área salen de la base, no del token. |
| Refresh | Token opaco de 256 bits en cookie `refresh_token` (`HttpOnly`, `SameSite=Lax`, `Path=/api/v1/auth`, `Secure` salvo `APP_COOKIE_SECURE=false`). En base solo se guarda su SHA-256. Vigencia absoluta de 7 días que la rotación no amplía. |
| Rotación y reúso | La rotación bloquea cuenta y sesión (`FOR UPDATE`) y guarda el hash consumido en `tokens_refresh_consumidos`. Presentar un hash consumido revoca la sesión en una transacción propia, ya confirmada cuando se responde 401. Dos renovaciones concurrentes: como máximo una prospera; la otra cuenta como reúso. |
| Logout | Con JWT revoca su sesión; con cookie revoca la sesión vigente o la que emitió un token ya rotado. Siempre elimina la cookie con el mismo alcance. |
| Primer ingreso | Con `debe_cambiar_contrasena = 1` solo se aceptan `PATCH /auth/password`, `POST /auth/refresh` y `POST /auth/logout`. El resto devuelve `403 CAMBIO_CONTRASENA_REQUERIDO`. |
| Cambio de contraseña | Valida la actual (400 si no coincide) y la política (8 a 72 caracteres con letras y números), guarda el hash BCrypt, limpia el indicador, revoca todas las sesiones y crea la nueva en una transacción. |
| Desactivación de cuenta | `PATCH /admin/users/{id}` con `activo: false` revoca todas las sesiones en la misma transacción. |
| Límite de login | 5 fallos por correo e IP en 15 minutos (`APP_LOGIN_MAX_ATTEMPTS`, `APP_LOGIN_WINDOW`) → `429`. Contador en memoria de la instancia. Los errores 401 no distinguen cuenta inexistente, contraseña incorrecta o cuenta inactiva. |
| Auto-registro | `POST /auth/register` (público, origen validado, 10 por IP y hora) crea un `ESTUDIANTE` sin área, `PENDIENTE` y con `origen_registro = AUTO_REGISTRO`; ignora el rol enviado. Solo dominio `@unmsm.edu.pe` exacto. Correo repetido: `409` sin datos de la cuenta. |
| Estado de cuenta en login | Con contraseña correcta, una cuenta `PENDIENTE` o `RECHAZADA` recibe `403 CUENTA_PENDIENTE` o `403 CUENTA_RECHAZADA` sin sesión. Con contraseña incorrecta recibe el `401` genérico, así el estado solo se revela a quien conoce la contraseña. |
| Aprobación de cuentas | `PATCH /admin/users/{id}/account-status` (solo A): `PENDIENTE → ACTIVA` o `PENDIENTE → RECHAZADA` con motivo; `RECHAZADA → ACTIVA` también. Guarda `revisado_por_id`, `revisado_en` y auditoría. |
| Restablecimiento | `POST /admin/users/{id}/password-reset` (solo A, nunca sobre sí mismo, solo cuentas activas): contraseña temporal de 12 caracteres con `SecureRandom`, mostrada una vez con `no-store`; hash BCrypt, `debe_cambiar_contrasena = 1` y revocación de sesiones en una transacción. La auditoría no guarda la temporal. |
| Primer administrador | `APP_BOOTSTRAP_ADMIN_EMAIL` y `APP_BOOTSTRAP_ADMIN_PASSWORD` crean un administrador solo si no existe ninguno; queda obligado a cambiar la contraseña. |

## Origen, CORS y CSRF

- CORS con orígenes explícitos (`APP_ALLOWED_ORIGINS`, por defecto `http://localhost:3000` y `http://127.0.0.1:3000`) y credenciales habilitadas para `/api/**`.
- El CSRF de Spring está desactivado porque las operaciones de negocio usan `Authorization`. Las operaciones con cookie (`/auth/login`, `/auth/refresh` y `/auth/logout` sin JWT) validan `Origin` (o `Referer`) contra la misma lista: un origen ajeno recibe `403 ORIGEN_NO_PERMITIDO`. Junto con `SameSite=Lax`, un sitio externo no puede usar la cookie.
- Clientes sin navegador (Postman, CI) no envían `Origin` y se aceptan.

## Rutas públicas

`GET /api/v1/status`, `GET /actuator/health` (sin detalles), `POST /api/v1/auth/login|register|refresh|logout`, `/v3/api-docs` y `/swagger-ui.html`. Todo lo demás exige actor autenticado (`401 NO_AUTENTICADO`). En despliegues fuera de local se recomienda restringir Actuator y Swagger a la red interna.

## Matriz por operación

Leyenda: **E** estudiante, **D** docente, **T** técnico, **S** supervisor, **A** administrador. «Alcance» indica el filtro por recurso; fuera de alcance se responde `404` y el recurso no se cuenta en listados. Un rol sin permiso para la operación recibe `403`.

| Operación | Roles | Alcance y restricciones |
| --- | --- | --- |
| `POST /auth/register` | Público | Solo estudiantes con dominio institucional; límite por IP. |
| `POST /auth/login` | Público | Cuenta activa; límite de intentos. |
| `POST /auth/refresh` | Cookie | Sesión vigente; origen permitido. |
| `POST /auth/logout` | JWT o cookie | Sesión propia. |
| `PATCH /auth/password` | Todos (incluido primer ingreso) | Cuenta propia. |
| `POST /requests` | E, D | Categoría, área y prioridad activas. |
| `GET /requests` | E, D, T, S | E/D: propias. T: asignación vigente, orden prioridad desc, fecha asc, id. S: su área, filtros `estado`, `categoriaId`, `prioridadId`. A: 403. |
| `GET /requests/{id}`, `/history`, `/comments`, `POST /comments` | E, D, T, S | Propietario, técnico con asignación vigente o supervisor del área. |
| `POST/GET /requests/{id}/attachments`, `GET …/{attachmentId}/content` | E, D, T, S | Misma regla que el detalle (404 fuera de alcance). Adjuntar solo con la solicitud abierta (409). Tipo por contenido, 5 MB y 5 vigentes. |
| `DELETE /requests/{id}/attachments/{attachmentId}` | E, D, T, S | Solo el autor (403) y con la solicitud abierta (409). |
| `GET /requests/{id}/eligible-technicians` | S | Supervisor del área. |
| `POST /requests/{id}/assignments` | S | Supervisor del área; técnico activo del área (400); solicitud terminal (409). |
| `PATCH /requests/{id}` | S | Supervisor del área; solicitud abierta (409); conserva el SLA. |
| `PATCH /requests/{id}/status` | T, S | T asignado: `EN_PROCESO`, `RESUELTO`. S del área: `CERRADO`, `RECHAZADO` con motivo. Destino sin transición para el rol: 403. Origen inválido o terminal: 409. |
| `GET /requests/{id}/audit-events` | S | Supervisor del área. |
| `POST /requests/{id}/survey` | E, D | Propietario; solo `CERRADO` (409); una por solicitud (409). |
| `GET /dashboard/summary`, `/reports` | S, A | S: su área (otra área 403). A: un área o todas. |
| `GET/POST/PATCH /admin/users`, `GET /admin/users/pending`, `PATCH /admin/users/{id}/account-status`, `POST /admin/users/{id}/password-reset`, `GET /admin/roles` | A | No puede cambiar su propio rol ni desactivarse (403). T/S requieren área activa. |
| `GET /catalogs/categories` | Todos | Solo categorías habilitadas. |
| `/admin/catalogs/*`, `/admin/sla-policies` | A | Códigos estables (400 al editarlos); bajas lógicas con control de dependencias activas (409). |
| `GET /spaces`, `GET /spaces/{id}/availability`, `POST /reservations` | Todos | Espacio y tipo activos (404). |
| `GET /reservations` | Todos | `vista=propias` (defecto). `vista=gestion`: S de su área o A, solo pendientes; otros 403. |
| `GET /reservations/{id}`, `/history` | Todos | Propietario, supervisor del área del espacio o A. Espacio sin área: solo A gestiona. |
| `PATCH /reservations/{id}/status` | Según destino | `CONFIRMADA`/`RECHAZADA`: S del área o A, sin autoaprobación (403), solo pendientes (409). `CANCELADA`: solo el propietario (incluso A recibe 403), antes del inicio (409). |
| `/admin/spaces`, `/admin/space-types`, `/admin/spaces/{id}/schedule`, `/admin/reservation-policy` | A | Aprobación obligatoria (`requiereAprobacion` siempre verdadero). |

## Casos negativos cubiertos por pruebas

`AuthIT`, `RequestLifecycleIT`, `SlaAndDashboardIT`, `AdminCatalogIT` y `ReservationIT` verifican, entre otros: credenciales inválidas indistinguibles, 429, primer ingreso, logout y cambio de contraseña que invalidan credenciales previas, reúso y refresh concurrente, vencimiento absoluto, origen ajeno, desactivación con revocación, acceso de otro estudiante, técnico o supervisor (404 y fuera del total), rutas administrativas para otros roles (403), autoaprobación, cancelación por administrador y decisión fuera del área.

## Datos sensibles

- Evidencias: la clave del objeto (`solicitudes/{id}/{uuid}.{ext}`) la genera el servidor; el nombre original solo se muestra. El almacenamiento local rechaza claves fuera de su carpeta. La descarga lleva `nosniff` y `attachment` para que el navegador no ejecute el contenido. Con Supabase, la clave de servicio vive solo en `APP_STORAGE_SUPABASE_SERVICE_KEY` y el frontend recibe únicamente URLs firmadas de 5 minutos.

- Contraseñas con BCrypt (costo 10); las respuestas nunca incluyen hashes.
- La auditoría guarda actor, campo y valores legibles; nunca tokens ni contraseñas.
- Los logs incluyen `correlationId` (`X-Correlation-ID`) y no registran cuerpos de petición. La prueba de humo de la imagen comprueba que los logs no contienen credenciales.
