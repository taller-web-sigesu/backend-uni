# Decisiones confirmadas y HU-26 a HU-29: plan de implementación

> **Para agentes:** SUB-SKILL OBLIGATORIA: usar superpowers:subagent-driven-development (recomendado) o superpowers:executing-plans para ejecutar este plan tarea por tarea. Los pasos usan casillas (`- [ ]`) para el seguimiento.

**Objetivo:** fijar en contrato, pruebas y documentación las decisiones que confirmó el equipo el 2026-11-13, e implementar evidencias (HU-26), auto-registro (HU-27), aprobación de cuentas (HU-28) y restablecimiento de contraseña (HU-29).

**Arquitectura:** se mantiene el monolito modular de `backend-uni` con `JdbcClient` y SQL explícito. Las tablas y columnas ya existen en `V1__esquema_inicial.sql` (`adjuntos_solicitud`, `usuarios.estado_cuenta`, `origen_registro`, `revisado_por_id`, `revisado_en`, `motivo_rechazo`), así que no hacen falta migraciones nuevas. Las evidencias pasan por una interfaz `AttachmentStorage` con dos adaptadores: disco local (volumen Docker), que es el activo ahora, y Supabase Storage, que se prueba contra un servidor simulado y se conecta en la Fase 5.

**Tecnologías:** Java 21, Spring Boot 4.1.2, JdbcClient, Flyway, MySQL 8.4.13, Testcontainers 2.0.5, `RestClient` de Spring para Supabase y `MockRestServiceServer` para probarlo.

**Especificación:**
- `infra-uni/docs/requirements/user-stories/`: HU-26, HU-27, HU-28 y HU-29.
- `infra-uni/DISENIO-BD.md`: secciones 6.2, 6.25, 7.9 y 7.10, y decisiones D-23 a D-26.
- `infra-uni/docs/decisiones/decisiones-01.md` y `decisiones-02.md`.
- `infra-uni/docs/adr/ADR-0004-despliegue-persistencia.md`, punto 10.

## Decisiones confirmadas (2026-11-13)

| Tema | Decisión |
| --- | --- |
| Persistencia | Se mantiene `JdbcClient`, sin JPA. |
| Cambios al DDL | Se adoptan y se documentan también en `infra-uni/DISENIO-BD.md`: `tokens_refresh_consumidos`, `franja_activa` con `uk_reservas_franja_activa`, `vw_reservas_operativas` en hora de Lima y `vw_ultimo_responsable`. |
| Contraseña actual incorrecta | `400 VALIDACION` con error en el campo `contrasenaActual`. |
| CSV de solicitudes | Encabezado `codigo,categoria,area,estado,prioridad,responsable,creadoEn`. |
| Administrador en `/requests` | `403`. Ve las solicitudes solo a través del dashboard y el CSV. |
| `ASIGNADO` por `PATCH /status` | `400 USAR_ASIGNACION`. Para asignar se usa `POST /assignments`. |
| Compose local | Se carga la demo con `APP_FLYWAY_LOCATIONS`, se define `APP_JWT_SECRET` y se añade un volumen para evidencias. |
| HU-26 a HU-29 | Se implementan ahora. Supabase queda listo, pero el adaptador activo en local es el de disco. |

Este plan también resuelve estas preguntas abiertas de las HU:
- **Login con cuenta pendiente o rechazada (HU-27 CA-06).** Si la contraseña es correcta, responde `403 CUENTA_PENDIENTE` o `403 CUENTA_RECHAZADA` sin emitir sesión. Si la contraseña es incorrecta o la cuenta está desactivada, responde el mismo `401` de siempre. Así el estado solo se revela a quien conoce la contraseña.
- **Correo ya registrado (HU-27 CA-02).** Responde `409 CORREO_REGISTRADO` con un mensaje genérico, sin nombre ni estado de la cuenta.
- **Alta de solicitud con evidencias (escenario 26.1).** El frontend crea la solicitud y luego sube cada archivo. `POST /requests` no cambia.
- **Descarga.** El adaptador local transmite el archivo desde el backend. El de Supabase responde `302` a una URL firmada de 300 segundos.
- **Restablecimiento de la propia contraseña.** El administrador recibe `403` y debe usar `PATCH /auth/password`. Solo se restablecen cuentas con `activo = 1` y `estado_cuenta = 'ACTIVA'`; las demás devuelven `409`.

## Restricciones globales

- Nunca editar ni confirmar en `main`. Usar una rama por tarea y título de PR `[backend] ...` o `[infra] ...`.
- Commits y PR **sin** `Co-Authored-By` ni «Generated with Claude Code», según la memoria del usuario.
- Commit, push y PR solo cuando el usuario lo pida explícitamente.
- Errores `application/problem+json` con `codigo`, `correlationId` y `errores[] {campo, mensaje}`. IDs como cadenas e instantes en UTC con formato ISO-8601.
- Política de contraseña: de 8 a 72 caracteres, con al menos una letra y un dígito (`UserIdentityService.validatePasswordPolicy`).
- Dominio del auto-registro: `unmsm.edu.pe` exacto, sin subdominios y sin distinguir mayúsculas. Se lee de `APP_REGISTRATION_EMAIL_DOMAIN` (por defecto `unmsm.edu.pe`).
- Evidencias:
  - Tipos `image/jpeg`, `image/png` y `application/pdf`, detectados por los primeros bytes: `FF D8 FF`, `89 50 4E 47 0D 0A 1A 0A` y `25 50 44 46 2D`.
  - De 1 a 5 242 880 bytes, y como máximo 5 vigentes por solicitud.
  - Clave `solicitudes/{solicitudId}/{uuid}.{jpg|png|pdf}`.
  - Bucket `evidencias-solicitudes`.
- Auditoría con `entidad` y `accion` exactas:
  - `usuarios`: `REGISTRO`, `APROBAR_CUENTA`, `RECHAZAR_CUENTA` y `RESTABLECER_CONTRASENA`.
  - `adjuntos_solicitud`: `ADJUNTAR` y `ELIMINAR_ADJUNTO`, con `solicitud_id`.
  - Nunca se guarda una contraseña ni una clave.
- La clave de servicio de Supabase solo vive en variables de entorno (`APP_STORAGE_SUPABASE_SERVICE_KEY`) y nunca aparece en logs ni respuestas.
- Las pruebas se ejecutan con `mvn -B verify`; sin Java en el host se usa el comando Docker del README. Si cambia el contrato: `cp target/openapi.yaml docs/api/openapi.yaml`.

## Foco de revisión

1. **Archivo con extensión engañosa.** Un `.exe` renombrado a `.jpg` o un PDF con `Content-Type: image/png` se rechaza o se guarda con el tipo detectado, nunca con el declarado. Prueba `rechazaContenidoNoPermitidoAunqueLaExtensionSeaJpg` (tarea 5).
2. **Dos cargas simultáneas cuando ya hay 4 evidencias.** Solo una prospera y el total vigente nunca supera 5. Prueba `cargasConcurrentesNoSuperanCinco` (tarea 5).
3. **Nombre original con ruta o caracteres de control** (`../../etc/passwd`, `a\u0000.pdf`). Se guarda el nombre saneado para mostrarlo; la clave del objeto nunca lo usa. La descarga lleva `Content-Disposition` con `filename*` codificado. Prueba `nombreConRutaNoAfectaLaClave` (tarea 5).
4. **Registro con mayúsculas o espacios** (` Ana@UNMSM.edu.pe `). Se normaliza y choca con la cuenta existente: `409`. El subdominio `@fisi.unmsm.edu.pe` se rechaza con `400`. Prueba `registroNormalizaCorreoYRechazaSubdominio` (tarea 3).
5. **Fallo del almacenamiento a mitad de la carga.** Si el `INSERT` falla después de subir el objeto, el objeto se borra. Si el borrado del objeto falla al eliminar una evidencia, la fila ya marcada se conserva y el error queda en el log sin la clave de servicio. Prueba `insertFallidoBorraElObjeto` (tarea 5).

---

### Tarea 0: Preparar ramas

**Requiere el visto bueno del usuario.** El trabajo actual de `backend-uni` está sin confirmar en `feature/implementacion-backend`.

- [ ] **Paso 1.** Con autorización, confirmar el trabajo actual en `feature/implementacion-backend` (sin líneas de atribución) y abrir su PR `[backend] Implementación HU-01 a HU-25`.
- [ ] **Paso 2.** Crear `feature/hu-26-29-y-decisiones` desde `feature/implementacion-backend`. Si esa PR ya se integró, crearla desde `main`.
- [ ] **Paso 3.** En `infra-uni`, que hoy está en `docs/enlaces-entre-repos`, crear `docs/sincronizar-ddl-y-compose` desde `main` actualizado.

### Tarea 1: Fijar las decisiones del contrato

**Archivos:**
- Modificar: `docs/api/autenticacion.md`, `docs/api/dashboard.md`, `docs/api/solicitudes.md` y `docs/api/atencion.md`.
- Pruebas: `src/test/java/.../requests/RequestLifecycleIT.java` y `src/test/java/.../requests/SlaAndDashboardIT.java`.

**Interfaces:** ninguna nueva.

- [ ] **Paso 1: Añadir las pruebas que fijan las decisiones.** Ya existen las de la contraseña incorrecta (`AuthIT`, 400) y la del administrador en `GET /requests` (403); no duplicarlas.

```java
// SlaAndDashboardIT
@Test void csvSolicitudesTieneEncabezadoExacto() {
    Response csv = api.get("/api/v1/dashboard/reports?tipo=solicitudes&fechaDesde=2026-01-01&fechaHasta=2027-01-01", api.token(admin));
    assertThat(csv.body().lines().findFirst().orElse(""))
        .isEqualTo("codigo,categoria,area,estado,prioridad,responsable,creadoEn");
}
// RequestLifecycleIT
@Test void asignadoPorStatusDevuelve400() {
    // supervisor del área, solicitud NUEVO
    Response r = api.patch("/api/v1/requests/" + id + "/status", supervisorToken, Map.of("estado", "ASIGNADO"));
    assertThat(r.status()).isEqualTo(400);
    assertThat(r.codigo()).isEqualTo("USAR_ASIGNACION");
}
@Test void adminRecibe403EnDetalleDeSolicitud() {
    assertThat(api.get("/api/v1/requests/" + id, adminToken).status()).isEqualTo(403);
}
```

- [ ] **Paso 2.** Ejecutar `mvn -B verify -Dit.test='RequestLifecycleIT,SlaAndDashboardIT'`. Se espera PASS, porque el comportamiento ya existe; si alguna falla, corregir el código para que cumpla la decisión.
- [ ] **Paso 3: Documentar en los `.md` de la API.**
  - `autenticacion.md`: «contraseña actual incorrecta → 400 `VALIDACION`, `errores[0].campo = contrasenaActual`».
  - `dashboard.md`: el encabezado exacto del CSV y qué significan `area` (nombre del área) y `responsable` (último técnico asignado o vacío).
  - `solicitudes.md`: el administrador recibe 403 en `/requests/**`.
  - `atencion.md`: `ASIGNADO` por `/status` → 400 `USAR_ASIGNACION`.
- [ ] **Paso 4.** Commit `docs(api): fijar decisiones de contrato confirmadas`.

### Tarea 2: Sincronizar el DDL y el Compose en infra-uni

**Archivos (repositorio `infra-uni`, rama `docs/sincronizar-ddl-y-compose`):**
- Modificar `DISENIO-BD.md`:
  - En el Anexo A, añadir la tabla `tokens_refresh_consumidos`, cambiar `uk_reservas_franja_exacta` por la columna generada `franja_activa` con `uk_reservas_franja_activa`, cambiar la definición de `vw_reservas_operativas` y añadir `vw_ultimo_responsable`.
  - En la sección 6, añadir una tabla nueva.
  - Añadir las decisiones D-28 a D-31.
- Modificar `compose.yaml`, en el servicio `backend` y en `volumes`.
- Modificar `README.md` con las cuentas demo, que son ficticias, y dónde se guardan las evidencias.

**Interfaces:**
- Consume: el texto exacto de esos objetos en `backend-uni/src/main/resources/db/migration/V1__esquema_inicial.sql`, copiado tal cual.
- Consume: las tablas de ajustes de `backend-uni/docs/DATABASE.md`, como justificación de D-28 a D-31.

- [ ] **Paso 1.** Copiar los cuatro objetos de `V1__esquema_inicial.sql` al Anexo A y añadir D-28 a D-31 con la decisión, el motivo y la alternativa descartada, siguiendo el formato de D-23.
- [ ] **Paso 2.** Añadir al servicio `backend` de `compose.yaml`:

```yaml
      APP_FLYWAY_LOCATIONS: classpath:db/migration,classpath:db/demo
      APP_JWT_SECRET: ${APP_JWT_SECRET:-desarrollo-local-no-usar-fuera-de-local-2026}
      APP_STORAGE_PROVIDER: local
      APP_STORAGE_LOCAL_DIR: /data/evidencias
```

  Añadir también el volumen `- evidencias_data:/data/evidencias` y declarar `evidencias_data:` en `volumes:`.
- [ ] **Paso 3.** Ejecutar `docker compose config --quiet` en `infra-uni`. Se espera que salga con 0 y sin errores.
- [ ] **Paso 4.** Ejecutar `docker compose up -d --build`, luego `curl -s -o /dev/null -w '%{http_code}' -H 'Content-Type: application/json' -d '{"correo":"ana.demo@universidad.example","contrasena":"Demo2026!"}' http://127.0.0.1:8080/api/v1/auth/login`. Se espera `200`.
- [ ] **Paso 5.** Commit `docs(bd): sincronizar DDL con el backend y cargar demo en compose local`.

### Tarea 3: Auto-registro y login con estado de cuenta (HU-27)

**Archivos:**
- Crear: `src/main/java/.../users/service/SelfRegistrationService.java`.
- Modificar:
  - `auth/controller/AuthController.java`, con `POST /register`.
  - `auth/dto/AuthDtos.java`.
  - `users/repository/UserRepository.java`.
  - `users/service/UserIdentityService.java`.
  - `auth/service/SessionService.java`.
  - `shared/config/SecurityConfiguration.java`, que añade la ruta pública.
  - `shared/config/AppProperties.java`.
  - `application.yml`.
  - `docs/api/autenticacion.md`.
  - `docs/SECURITY.md`.
- Pruebas: `src/test/java/.../auth/RegistrationIT.java`.

**Interfaces:**
- Produce `record RegisterRequest(@NotBlank @Size(max=150) String nombreCompleto, @NotBlank @Email @Size(max=150) String correo, @NotBlank String contrasena, @NotBlank String confirmacionContrasena)`. Los campos desconocidos, como `rol`, se ignoran.
- Produce `record RegisterResponse(String mensaje)`, con mensaje = «Registro enviado. Podrás ingresar cuando un administrador apruebe tu cuenta».
- Produce `long UserRepository.insertSelfRegistered(String nombre, String correo, String hash, Instant now)`. Inserta `rol ESTUDIANTE`, `area_id NULL`, `estado_cuenta 'PENDIENTE'`, `origen_registro 'AUTO_REGISTRO'` y `debe_cambiar_contrasena 0`.
- Produce `RegisterResponse SelfRegistrationService.register(RegisterRequest body, String ip)`, que es `@Transactional`.
- Produce `sealed interface AuthenticationResult permits Authenticated, Pending, Rejected, Invalid` en `UserIdentityService`.
- Cambia `UserIdentityService.authenticate(String, String)` para que devuelva `AuthenticationResult` en lugar de `Optional<UserRecord>`.
- Produce `AppProperties.Registration(String emailDomain, int maxPerIpPerHour)`, con valores por defecto `unmsm.edu.pe` y `10`.

- [ ] **Paso 1: Escribir las pruebas que deben fallar en `RegistrationIT`.**
  - `registroCreaCuentaPendienteEstudiante`: espera 201 y el mensaje exacto. En la base, `estado_cuenta='PENDIENTE'`, `origen_registro='AUTO_REGISTRO'`, rol `ESTUDIANTE`, `area_id IS NULL`, `debe_cambiar_contrasena=0` y un evento de auditoría `usuarios/REGISTRO`.
  - `registroIgnoraRolEnviado`: el cuerpo trae `"rol":"ADMINISTRADOR"` y la cuenta queda como `ESTUDIANTE`.
  - `registroRechazaDominioAjeno`: `@gmail.com` devuelve 400 con `errores[0].campo = "correo"`.
  - `registroNormalizaCorreoYRechazaSubdominio`: ` Ana@UNMSM.edu.pe ` después de `ana@unmsm.edu.pe` devuelve 409 `CORREO_REGISTRADO`, y `x@fisi.unmsm.edu.pe` devuelve 400.
  - `registroValidaConfirmacionYPolitica`: con confirmación distinta devuelve 400 en el campo `confirmacionContrasena`; con `corta` devuelve 400 en el campo `contrasena`.
  - `loginPendienteYRechazado`: con la contraseña correcta, devuelve 403 `CUENTA_PENDIENTE` o 403 `CUENTA_RECHAZADA` sin `Set-Cookie`; con la contraseña incorrecta devuelve 401 `CREDENCIALES_INVALIDAS`.
  - `registroDesdeOrigenAjenoDevuelve403`: con `Origin: https://malicioso.example` devuelve 403 `ORIGEN_NO_PERMITIDO`.
  - `registroLimitadoPorIp`: la petición 11 en una hora desde la misma IP devuelve 429.
- [ ] **Paso 2.** Ejecutar `mvn -B verify -Dit.test=RegistrationIT`. Se espera FAIL con 404 en `/auth/register`.
- [ ] **Paso 3: Implementar.**
  - El dominio se compara con `correo.endsWith("@" + domain)` después de `normalizeEmail`.
  - El duplicado se detecta con `emailExists`; si dos registros compiten y se viola `uk_usuarios_correo` (`DuplicateKeyException`), también se responde 409.
  - `OriginGuard` se aplica igual que en login.
  - El límite por IP reutiliza `LoginAttemptLimiter` con la clave `register|<ip>` y una ventana de 1 h.
  - `SessionService.login` traduce `Pending` y `Rejected` a `ApiException.forbidden("CUENTA_PENDIENTE"|"CUENTA_RECHAZADA", …)` y no cuenta esos casos como fallo en el limitador.
- [ ] **Paso 4.** Ejecutar `mvn -B verify -Dit.test='RegistrationIT,AuthIT'`. Se espera PASS en ambas.
- [ ] **Paso 5.** Commit `feat(auth): auto-registro de estudiantes con aprobación pendiente (HU-27)`.

### Tarea 4: Aprobar o rechazar cuentas y restablecer contraseña (HU-28 y HU-29)

**Archivos:**
- Crear: `src/main/java/.../users/service/AccountReviewService.java` y `users/service/PasswordResetService.java`.
- Modificar:
  - `users/controller/AdminUserController.java`.
  - `users/dto/UserDtos.java`, que añade `estadoCuenta`, `origenRegistro` y `creadoEn` a `UserResponse`.
  - `users/repository/UserRepository.java`.
  - `users/repository/UserRecord.java`.
  - `docs/api/usuarios.md`.
  - `docs/SECURITY.md`.
- Pruebas: `src/test/java/.../users/AccountAdminIT.java`.

**Interfaces:**
- Consume `SessionRevocation.revokeAllForUser(long)`, `AuditEventService.record(...)`, `UserIdentityService.validatePasswordPolicy` y `PasswordEncoder`.
- Produce `GET /api/v1/admin/users/pending?page&size`, que devuelve `PageResponse<PendingAccountResponse>` ordenado por `creado_en ASC, id ASC`. El registro es `record PendingAccountResponse(String id, String nombreCompleto, String correo, String creadoEn, String estadoCuenta)`.
- Produce `PATCH /api/v1/admin/users/{id}/account-status` con el cuerpo `record AccountDecisionRequest(@NotNull EstadoDecision estadoCuenta, @Size(max=500) String motivo)`. `EstadoDecision` es `ACTIVA` o `RECHAZADA`. Responde 200 con `UserResponse`.
- Produce `POST /api/v1/admin/users/{id}/password-reset`, que responde 200 con `record PasswordResetResponse(String contrasenaTemporal)` y la cabecera `Cache-Control: no-store`.
- Produce `void UserRepository.review(long id, String estado, long reviewerId, Instant now, String motivo)`.
- Produce `List<UserRecord> UserRepository.pending(PageQuery)` y `long countPending()`.
- Produce `String PasswordResetService.temporaryPassword()`: 12 caracteres de `SecureRandom` sobre `ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789`, con al menos una letra y un dígito garantizados.

- [ ] **Paso 1: Escribir las pruebas que deben fallar en `AccountAdminIT`.**
  - `bandejaPendientesOrdenadaMasAntiguaPrimero`: con tres registros, devuelve los ids en orden de creación y `total = 3`; las cuentas activas no aparecen.
  - `aprobarPermiteLogin`: `estadoCuenta ACTIVA` devuelve 200; luego el login del estudiante devuelve 200; en la base quedan `revisado_por_id = admin` y `revisado_en` no nulo, más la auditoría `APROBAR_CUENTA`.
  - `rechazarExigeMotivo`: sin `motivo` devuelve 400 con `errores[0].campo = "motivo"`; con motivo devuelve 200, `motivo_rechazo` guardado, auditoría `RECHAZAR_CUENTA` y login 403 `CUENTA_RECHAZADA`.
  - `rechazadaPuedeAprobarseDespues`: `RECHAZADA` → `ACTIVA` devuelve 200, y `motivo_rechazo` vuelve a `NULL`.
  - `decidirCuentaActivaDevuelve409`: una cuenta creada por el administrador devuelve 409 `CUENTA_NO_PENDIENTE`.
  - `bandejaYDecisionSoloAdmin`: el supervisor recibe 403 en las dos rutas (escenario 28.4).
  - `restablecerContrasena`: responde 200 con `contrasenaTemporal` que cumple la política y `Cache-Control: no-store`. Después, el token previo del usuario recibe 401; el login con la temporal funciona; `GET /requests` devuelve 403 `CAMBIO_CONTRASENA_REQUERIDO`; hay un evento `RESTABLECER_CONTRASENA` cuyos valores no contienen la temporal.
  - `restablecerSinPermisoOInvalido`: el técnico recibe 403 (escenario 29.4); el administrador sobre sí mismo recibe 403; sobre una cuenta pendiente o desactivada recibe 409; sobre un id inexistente recibe 404.
- [ ] **Paso 2.** Ejecutar `mvn -B verify -Dit.test=AccountAdminIT`. Se espera FAIL con 404.
- [ ] **Paso 3: Implementar.**
  - Cada operación es una transacción que bloquea la cuenta con `lockById`.
  - El restablecimiento usa `updatePassword(id, hash, true)` y `revokeAllForUser` en la misma transacción.
  - Declarar `/users/pending` antes de `/users/{id}`, porque `{id}` es `long` y la ruta literal debe ganar.
- [ ] **Paso 4.** Ejecutar `mvn -B verify -Dit.test='AccountAdminIT,AuthIT'`. Se espera PASS.
- [ ] **Paso 5.** Commit `feat(admin): aprobar cuentas y restablecer contraseñas (HU-28, HU-29)`.

### Tarea 5: Evidencias con almacenamiento local (HU-26)

**Archivos:**
- Crear en `src/main/java/.../attachments/`:
  - `storage/AttachmentStorage.java` y `storage/LocalAttachmentStorage.java`.
  - `domain/FileTypeDetector.java`.
  - `repository/AttachmentRepository.java` y `repository/AttachmentRow.java`.
  - `service/AttachmentService.java`.
  - `controller/AttachmentController.java`.
  - `dto/AttachmentDtos.java`.
- Modificar:
  - `shared/config/AppProperties.java`, que añade `Storage(String provider, String localDir, Supabase supabase)`.
  - `application.yml`: `spring.servlet.multipart.max-file-size: 6MB`, `max-request-size: 6MB` y `app.storage.*`.
  - `shared/web/ApiExceptionHandler.java`: `MaxUploadSizeExceededException` responde 400 con el campo `archivo`.
  - `docs/api/solicitudes.md` y `docs/SECURITY.md`.
- Pruebas:
  - `src/test/java/.../attachments/AttachmentIT.java`.
  - `src/test/java/.../domain/FileTypeDetectorTest.java`.
  - Soporte: `Api.multipart(String path, String token, String filename, String contentType, byte[] content)`.

**Interfaces:**
- Consume `RequestAccessService.requireVisible(actor, id)`, `ServiceRequestRepository.lockById(id)`, `RequestRow.esFinal()`, `responsableId()` y `areaId()`, y `AuditEventService.record(...)`.
- Produce lo siguiente:
  - `interface AttachmentStorage { String provider(); void put(String key, byte[] content, String mime); void delete(String key); Optional<URI> signedDownloadUrl(String key, Duration ttl); InputStream open(String key); }`. El adaptador local devuelve `Optional.empty()` en `signedDownloadUrl`.
  - `enum DetectedType { JPEG("image/jpeg","jpg"), PNG("image/png","png"), PDF("application/pdf","pdf") }` con `static Optional<DetectedType> FileTypeDetector.detect(byte[] head)`.
  - `record AttachmentResponse(String id, String nombre, long tamanoBytes, String tipoMime, String autorId, String autorNombre, String creadoEn, boolean puedeEliminar)`.
  - `POST /api/v1/requests/{id}/attachments` (multipart, campo `archivo`) responde 201 con `AttachmentResponse`.
  - `GET /api/v1/requests/{id}/attachments` responde 200 con `List<AttachmentResponse>` de las vigentes, ordenadas por `creado_en ASC`.
  - `GET /api/v1/requests/{id}/attachments/{attachmentId}/content` responde 200 con el binario y `Content-Disposition: attachment; filename*=UTF-8''…`, o 302 a la URL firmada.
  - `DELETE /api/v1/requests/{id}/attachments/{attachmentId}` responde 204.
  - `LocalAttachmentStorage` se activa con `@ConditionalOnProperty(name="app.storage.provider", havingValue="local", matchIfMissing=true)`. Antes de escribir, resuelve `localDir.resolve(key).normalize()` y comprueba que la ruta siga dentro de `localDir`.

- [ ] **Paso 1: Escribir `FileTypeDetectorTest`.**
  - Las firmas de JPEG, PNG y PDF se detectan.
  - `MZ` (exe), un arreglo vacío, `GIF89a` y 3 bytes de JPEG truncado devuelven `Optional.empty()`.
- [ ] **Paso 2: Escribir las pruebas de `AttachmentIT`.** Usan `app.storage.local-dir` en un `@TempDir` mediante `DynamicPropertySource`.
  - `solicitanteAdjuntaYVeLaEvidencia` (26.1): un JPEG de 2 MB responde 201. El listado muestra nombre, tamaño, autor y fecha. La descarga devuelve los mismos bytes. Hay un evento `adjuntos_solicitud/ADJUNTAR` con `solicitud_id`.
  - `rechazaContenidoNoPermitidoAunqueLaExtensionSeaJpg` (26.2): `MZ…` como `foto.jpg` con `image/jpeg` responde 400 con el campo `archivo` y el mensaje «Solo se aceptan archivos JPG, PNG o PDF.». En el disco no se escribe nada.
  - `rechazaArchivoMayorA5MB` (26.3): un PDF de 8 MB responde 400 con el campo `archivo` y el mensaje «El tamaño máximo es 5 MB.». Un archivo de 5 242 880 bytes se acepta.
  - `maximoCincoVigentes`: el sexto responde 409 `LIMITE_EVIDENCIAS`. Al eliminar uno, el siguiente se acepta.
  - `cargasConcurrentesNoSuperanCinco`: con 4 vigentes se lanzan 2 cargas en paralelo; resulta exactamente un 201 y un 409, y quedan 5 filas vigentes.
  - `accesoIndebidoNoRevelaExistencia` (26.4): otro estudiante recibe 404 en el listado, la descarga y la eliminación. Un id de evidencia de otra solicitud consultado bajo una solicitud visible también devuelve 404.
  - `tecnicoYSupervisorAdjuntan`: el técnico asignado y el supervisor del área reciben 201. Un técnico sin asignación recibe 404.
  - `autorEliminaConAuditoria` (26.5): con la solicitud `ASIGNADO`, el autor recibe 204, la evidencia sale del listado, quedan `eliminado_en` y `eliminado_por_id`, hay auditoría `ELIMINAR_ADJUNTO` y el archivo ya no existe en el disco.
  - `soloElAutorElimina`: el supervisor que ve la solicitud recibe 403 al eliminar la evidencia del estudiante.
  - `solicitudCerradaORechazadaNoAdmiteCambios`: tanto la carga como la eliminación devuelven 409 `SOLICITUD_FINALIZADA`; la descarga sigue funcionando.
  - `nombreConRutaNoAfectaLaClave`: con `../../x.pdf`, la clave coincide con `^solicitudes/\d+/[0-9a-f-]{36}\.pdf$` y el nombre mostrado es `x.pdf`.
  - `insertFallidoBorraElObjeto`: se usa un `AttachmentRepository` espía que lanza al insertar. La respuesta es 500 y el directorio queda vacío.
  - `adminRecibe403`: el administrador recibe 403 en las cuatro rutas, coherente con la decisión sobre `/requests`.
- [ ] **Paso 3.** Ejecutar `mvn -B verify -Dtest=FileTypeDetectorTest -Dit.test=AttachmentIT`. Se espera FAIL porque las clases no existen.
- [ ] **Paso 4: Implementar.** Orden dentro de `AttachmentService.upload`:
  1. Validar el tamaño y el tipo por contenido, antes de la transacción.
  2. Calcular el SHA-256.
  3. Abrir la transacción y hacer `lockById` de la solicitud (404 si no es visible, 409 si `esFinal`).
  4. Contar las vigentes; si ya hay 5, responder 409.
  5. Llamar a `storage.put`.
  6. Insertar la fila y la auditoría.
  7. Si algo falla después de `put`, llamar a `storage.delete` en el `catch` y volver a lanzar la excepción.

  Para eliminar: transacción con `lockById`, marcar la fila y auditar; después del commit (`TransactionSynchronization.afterCommit`) se llama a `storage.delete`, y si falla se registra en el log con la clave del objeto. El nombre mostrado conserva solo el último segmento de la ruta, sin caracteres de control y recortado a 255.
- [ ] **Paso 5.** Ejecutar `mvn -B verify`. Se espera PASS en toda la suite.
- [ ] **Paso 6.** Commit `feat(requests): evidencias con almacenamiento local (HU-26)`.

### Tarea 6: Adaptador de Supabase Storage

**Archivos:**
- Crear: `src/main/java/.../attachments/storage/SupabaseAttachmentStorage.java`.
- Modificar:
  - `AppProperties.Storage.Supabase(String url, String serviceKey, String bucket)`, con `bucket` por defecto `evidencias-solicitudes`.
  - `application.yml`: `APP_STORAGE_SUPABASE_URL`, `APP_STORAGE_SUPABASE_SERVICE_KEY` y `APP_STORAGE_SUPABASE_BUCKET`.
  - `README.md` con la tabla de variables.
  - `docs/SECURITY.md`.
- Pruebas: `src/test/java/.../attachments/SupabaseAttachmentStorageTest.java`, unitaria con `MockRestServiceServer`.

**Interfaces:**
- Consume `AttachmentStorage` (tarea 5).
- Produce `SupabaseAttachmentStorage(RestClient.Builder builder, AppProperties props)` con `@ConditionalOnProperty(name="app.storage.provider", havingValue="supabase")`. Al arrancar, falla si `url` o `serviceKey` están vacíos.
- Las llamadas a la API REST de Storage llevan `Authorization: Bearer <serviceKey>` y `apikey: <serviceKey>`:
  - **put:** `POST {url}/storage/v1/object/{bucket}/{key}` con `Content-Type: <mime>` y `x-upsert: false`.
  - **delete:** `DELETE {url}/storage/v1/object/{bucket}` con el cuerpo `{"prefixes":["<key>"]}`.
  - **signedDownloadUrl:** `POST {url}/storage/v1/object/sign/{bucket}/{key}` con `{"expiresIn":300}`. La respuesta trae `{"signedURL":"/object/sign/..."}` y se devuelve `URI.create(url + "/storage/v1" + signedURL)`.
  - **open:** lanza `UnsupportedOperationException`, porque la descarga siempre usa la URL firmada.

- [ ] **Paso 1: Escribir las pruebas que deben fallar.**
  - `putEnviaCabecerasYRuta`: comprueba el método, la URI y que se envían las dos cabeceras de autenticación.
  - `deleteEnviaPrefijos`: comprueba el cuerpo JSON exacto.
  - `firmaDevuelveUrlAbsoluta`: comprueba la URI resultante.
  - `errorDelProveedorNoFiltraLaClave`: ante un 500 del proveedor se lanza `IllegalStateException`, y su mensaje no contiene `serviceKey`.
  - `faltaConfiguracionFallaAlArrancar`.
- [ ] **Paso 2.** Ejecutar `mvn -B test -Dtest=SupabaseAttachmentStorageTest`. Se espera FAIL.
- [ ] **Paso 3.** Implementar con `RestClient`. Hacer que el controlador de la tarea 5 responda `302` con `Location` cuando `signedDownloadUrl` devuelva un valor; añadir a `AttachmentIT` la prueba `descargaRedirigeConProveedorFirmado`, que usa un `AttachmentStorage` de prueba que devuelve una URI y espera 302 con la URI exacta.
- [ ] **Paso 4.** Ejecutar `mvn -B verify`. Se espera PASS.
- [ ] **Paso 5.** Commit `feat(storage): adaptador de Supabase Storage para evidencias`.

### Tarea 7: Contrato OpenAPI, documentación y verificación final

**Archivos:**
- Modificar: `src/test/java/.../OpenApiContractIT.java`, `docs/api/openapi.yaml`, `README.md`, `ARCHITECTURE.md` y `docs/DATABASE.md`.
- Modificar `scripts/smoke-image.sh`: añadir el registro y su login, que debe dar 403 `CUENTA_PENDIENTE`.

- [ ] **Paso 1: Añadir las 8 rutas nuevas a `EXPECTED`** y cambiar `hasSize(56)` por `hasSize(64)`:
  - `POST /api/v1/auth/register`
  - `GET /api/v1/admin/users/pending`
  - `PATCH /api/v1/admin/users/{id}/account-status`
  - `POST /api/v1/admin/users/{id}/password-reset`
  - `POST /api/v1/requests/{id}/attachments`
  - `GET /api/v1/requests/{id}/attachments`
  - `GET /api/v1/requests/{id}/attachments/{attachmentId}/content`
  - `DELETE /api/v1/requests/{id}/attachments/{attachmentId}`
- [ ] **Paso 2.** Ejecutar `mvn -B verify` y luego `cp target/openapi.yaml docs/api/openapi.yaml`. Ejecutar `mvn -B verify` otra vez y después `diff -q docs/api/openapi.yaml target/openapi.yaml`. Se espera PASS y que no haya diferencias.
- [ ] **Paso 3.** Ejecutar `bash scripts/smoke-image.sh`. Se espera que la salida termine en `Prueba de humo superada`.
- [ ] **Paso 4.** Actualizar el README (HU-01 a HU-29 y las variables `APP_REGISTRATION_*` y `APP_STORAGE_*`), `ARCHITECTURE.md` (módulo `attachments`) y `DATABASE.md` (convenciones de evidencias).
- [ ] **Paso 5.** Commit `docs: contrato y documentación de HU-26 a HU-29`. Si el usuario lo autoriza, abrir la PR `[backend] HU-26 a HU-29 y decisiones de contrato`, y en `infra-uni` la PR `[infra] Sincronizar DDL y compose local`.

## Fuera de este plan

- Crear la cuenta y los proyectos de Supabase (staging y producción) es tarea de la Fase 5, según `decisiones-01.md`. Hasta entonces no hace falta ninguna cuenta: `APP_STORAGE_PROVIDER=local` cubre local y CI.
- Pantallas de frontend para el registro, la bandeja de cuentas y las evidencias (`frontend-uni`).
