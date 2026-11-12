# Fase 01 · Fundaciones y sesión

**Objetivo.** Arrancar el backend sobre MySQL desde una base vacía y habilitar sesiones y autorización por rol para HU-01 y HU-02.

**Entrada.** Contratos y esquema revisados en la [fase 00](00-contratos-y-decisiones.md). Consultar el [contrato de autenticación](../api/autenticacion.md), el [contrato operativo](../api/operacion.md) y el [ADR de sesión](../../../infra-uni/docs/adr/ADR-0002-autenticacion-sesion.md).

## Tareas

- [ ] **Preparar Maven y configuración.** Añadir Spring Security, Bean Validation, Flyway con soporte MySQL y springdoc. Incorporar JUnit, Mockito, Spring Boot Test y Testcontainers MySQL. Conservar Java 21, datasource por entorno, JDBC en UTC y `ddl-auto: none`. Verificar resolución y compilación antes de añadir módulos.
- [ ] **Versionar el esquema.** Crear `src/main/resources/db/migration/V1__esquema_inicial.sql` con tablas, vistas, claves, checks e índices del DDL reconciliado, omitiendo `CREATE DATABASE` y `USE`. Crear `V2__catalogos_semilla.sql` con roles, estados, transiciones y catálogos base. Preparar los datos ficticios como carga separada activada explícitamente en local o CI. Verificar migración de una base vacía y segunda ejecución sin cambios.
- [ ] **Implementar el módulo `shared`.** Centralizar errores, validación, paginación, correlación y configuración de seguridad. La cadena de seguridad deniega operaciones protegidas sin actor autenticado. Mantener `GET /api/v1/status` con el contrato existente y publicar en salud solo los datos operativos que permita el contrato. Autorizar orígenes explícitos y aplicar los controles de origen y CSRF acordados en fase 00 a las operaciones de navegador con cookie.
- [ ] **Crear la identidad mínima en `users`.** Leer cuenta activa, hash de contraseña, rol, área e indicador de primer ingreso a través de `UserIdentityService`. Aprovisionar el primer administrador por procedimiento local con credenciales suministradas por entorno. La gestión administrativa de cuentas se completa en fase 02.
- [ ] **Implementar login y acceso protegido en `auth`.** `POST /auth/login` valida la cuenta, aplica el límite de intentos y emite JWT de 15 minutos y refresh opaco. Guardar el hash del refresh y vencimiento absoluto de siete días. El JWT referencia su sesión. En cada petición protegida consultar que sesión y cuenta sigan activas y usar rol y área vigentes. Si se exige cambio inicial, las operaciones de negocio devuelven 403 con `CAMBIO_CONTRASENA_REQUERIDO`.
- [ ] **Implementar refresh y logout.** `POST /auth/refresh` rota bajo bloqueo transaccional, registra el hash consumido y conserva el vencimiento original. Ante reúso, confirmar la revocación antes de emitir 401. `POST /auth/logout` revoca la sesión actual y elimina la cookie con el mismo alcance que al crearla. Usar `HttpOnly`, `SameSite=Lax` y `Secure`, con excepción de HTTP en el perfil local. Emitir `Cache-Control: no-store` donde lo indique el contrato.
- [ ] **Implementar `PATCH /auth/password`.** Validar la contraseña actual y la nueva, guardar el nuevo hash, desactivar el indicador de primer ingreso, revocar sesiones previas y crear la nueva sesión en una transacción. Entregar las credenciales tras confirmar. Coordinar emisión, renovación y desactivación de cuenta para evitar nuevas sesiones con datos obsoletos.

## Verificación de la fase

| Caso | Resultado esperado |
| --- | --- |
| Migración y reinicio | Vistas e índices existen, las semillas se aplican una sola vez y el backend arranca. |
| Login inválido, cuenta inactiva o límite de intentos | El estado HTTP coincide con el contrato y el mensaje no expone información de la cuenta. |
| Primer ingreso | Acceso a cambio de contraseña, renovación y logout. Operaciones de negocio devuelven 403. |
| Logout y cambio de contraseña | Las credenciales revocadas fallan en la siguiente petición. Solo la nueva sesión funciona tras el cambio. |
| Refresh concurrente y reúso | Como máximo una rotación prospera. El reúso detectado revoca de forma persistente la sesión. |
| Expiración y origen no autorizado | La rotación no amplía los siete días y un origen fuera de la lista no puede utilizar el flujo con cookie. |

**Cierre.** Las migraciones y los escenarios de sesión pasan contra MySQL real mediante Testcontainers. Las operaciones de negocio reciben el actor y el alcance vigentes desde la capa de seguridad.
