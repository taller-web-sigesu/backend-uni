# Arquitectura del backend — backend-uni

> **Estado:** aplicación Spring Boot base implementada; plan de implementación aprobado, módulos de negocio pendientes.
> **Última actualización:** 2026-11-02.
> **Alcance:** API, reglas de negocio, autorización y persistencia.

## Referencias y propiedad

La [arquitectura global](../infra-uni/docs/ARCHITECTURE.md) define los límites y decisiones compartidas. El [diseño del backend](docs/BACKEND-DISENIO.md) detalla los archivos propuestos por módulo y su relación con las rutas. Leer [AGENTS.md](AGENTS.md) y la historia correspondiente en [user-stories](../infra-uni/docs/requirements/user-stories/) antes de cambiar comportamiento.

## Estado actual comprobado en archivos

- `pom.xml`: Java 21 y Spring Boot 4.1.2; Spring Web MVC, Data JPA, Actuator, conector MySQL y DevTools.
- `src/main/java/pe/edu/universidad/servicios/BackendApplication.java`: arranque de la aplicación.
- `status/StatusController.java`: `GET /api/v1/status`, devuelve `service: backend` y `status: UP`; no comprueba por sí mismo la base de datos.
- `src/main/resources/application.yml`: datasource por variables de entorno, `ddl-auto: none`, `open-in-view: false`, zona JDBC UTC y exposición de salud en `/actuator/health`.
- `Dockerfile.dev`: entorno de desarrollo usado por Compose.
- Aún no existen módulos de negocio, entidades, migraciones, OpenAPI, Spring Security, Bean Validation, Flyway ni pruebas configuradas en el proyecto.

La revisión de archivos no confirma compilación, ejecución ni conexión real a MySQL.

## Componentes y API objetivo

**Nombre:** API de Servicios Universitarios, única aplicación Spring Boot.

**Tecnologías propuestas:** Java, Spring Boot, Spring Web, Spring Security, Bean Validation, Spring Data JPA, Flyway, MySQL, Maven y OpenAPI.

**Despliegue propuesto:** contenedor Docker en Render o plataforma equivalente; disponibilidad, límites y costes pendientes de confirmar.

| Módulo lógico | Responsabilidad | Restricciones importantes |
| --- | --- | --- |
| `auth` | Inicio, renovación y cierre de sesión, y cambio de contraseña | Contraseñas hasheadas y errores sin filtrar información sensible |
| `users` | Perfil, usuarios y roles | RBAC; usuarios no se asignan roles privilegiados a sí mismos |
| `requests` | Registro, bandejas, detalle, historial, comentarios y satisfacción | Consultar solo recursos propios o autorizados |
| `management` | Asignación, prioridad y transición de estados | Coordina los cambios mediante los servicios de solicitudes |
| `catalogs` | Categorías, prioridades, áreas y estados configurables | Restringir operaciones administrativas; preservar integridad histórica |
| `dashboard` | Agregaciones y reportes de atención | Métricas desde datos reales, con filtros y definiciones documentadas |
| `sla` | Configuración y resolución de plazos | Precedencia por categoría y conservación del plazo asignado |
| `audit` | Registro y consulta de cambios de negocio | Eventos dentro de la transacción y consultas con alcance autorizado |
| `reservations` | Espacios, horarios, disponibilidad, reservas, aprobación y cancelación | Solapamientos validados en transacción; aprueba el supervisor del área o el administrador |
| `shared` | Seguridad común, configuración, errores y observabilidad | Sin reglas de negocio específicas del dominio |

Cada módulo debe separar entrada HTTP (controller/DTO), aplicación (service), dominio/persistencia (entity/repository) y pruebas. Un controller no ejecuta directamente reglas complejas ni consultas SQL ad hoc. Un DTO público no debe ser una entidad JPA expuesta.

La API REST usa el prefijo `/api/v1`. Las rutas propuestas, su seguridad y sus ejemplos están en el [diseño de API por negocio](docs/API-DISENIO.md).

**Estados obligatorios del proyecto:** `Nuevo → Asignado → En proceso → Resuelto → Cerrado`, con `Rechazado` como estado final alternativo. La matriz de transiciones por rol se define en la tabla `transiciones_estado`; saltos, reaperturas y cancelaciones requieren regla de negocio explícita en historia aprobada; no los inventa un agente.

El contrato aprobado se mantendrá en `docs/api/openapi.yaml` cuando se implemente.

## Organización objetivo

```text
src/main/java/pe/edu/universidad/servicios/
├── auth/
├── users/
├── requests/
├── management/
├── catalogs/
├── reservations/
├── dashboard/
├── sla/
├── audit/
├── status/
└── shared/
src/main/resources/db/migration/  # Migraciones Flyway previstas
src/test/                        # Pruebas previstas
docs/api/openapi.yaml            # Contrato REST previsto
docs/DATABASE.md                 # Documentación técnica de datos prevista
docs/SECURITY.md                 # Matriz de permisos prevista
```

Cada módulo mantiene sus controllers/DTO, services y entities/repositories; crear carpetas al implementar funcionalidades. Mantener un único desplegable Spring Boot, sin llamadas HTTP entre módulos internos.

## Persistencia objetivo

### Base de datos transaccional

- **Nombre:** Base de datos principal de servicios universitarios.
- **Motor:** **MySQL**, indicado explícitamente en la propuesta académica.
- **Uso:** cuentas, roles, solicitudes, catálogos, asignaciones, estados, comentarios, referencias de evidencias e historial.
- **Tablas candidatas:** `roles`, `usuarios`, `sesiones_usuario`, `areas`, `prioridades`, `categorias`, `estados_solicitud`, `transiciones_estado`, `politicas_sla`, `solicitudes`, `historial_estados`, `asignaciones`, `comentarios`, `encuestas_satisfaccion`, `auditoria_eventos` y `secuencias_codigo_solicitud`; `adjuntos_solicitud` solo si se aprueba la carga de evidencias.
- **Reservas de ambientes (EP-07):** `tipos_espacio`, `espacios`, `horarios_espacio`, `estados_reserva`, `politicas_reserva`, `reservas`, `historial_estados_reserva` y `secuencias_codigo_reserva`.
- **Integridad:** claves foráneas, índices para búsquedas reales, unicidad de correo/código, transacciones y migraciones Flyway versionadas.
- **Propiedad:** backend; no consultas directas a MySQL desde React.
- **Origen del esquema:** `backend-uni/src/main/resources/db/migration/`; el diseño vigente se documenta en `infra-uni/DISENIO-BD.md` y, cuando existan, las migraciones representarán la estructura implementada.

### Evidencias adjuntas

- **Estado:** diseño pendiente hasta definir requisitos de carga, límites y proveedor.
- **Uso propuesto:** fotos/documentos de solicitudes, con metadatos en MySQL y archivos en almacenamiento persistente separado.
- **Seguridad:** validar tipo, contenido y tamaño; exigir autorización también al descargar. No almacenar archivos críticos en disco efímero del contenedor ni registrar URLs públicas sin protección.

### Almacenes excluidos por defecto

No se requiere Redis, Elasticsearch, RabbitMQ, un almacén analítico ni una segunda base de datos para el MVP. Toda adición requiere una necesidad medida y un ADR.

El [diseño de base de datos](../infra-uni/DISENIO-BD.md) existe como documento compartido. `docs/DATABASE.md` y las migraciones aún están pendientes; las migraciones serán la referencia del esquema implementado. La configuración actual no crea tablas automáticamente.

## Seguridad objetivo

- **Autenticación:** Spring Security con **JWT de acceso de corta duración** como opción de diseño inicial. Mantenerlo en memoria del cliente para el MVP, sin persistirlo en `localStorage`; si se agrega renovación con cookies, revisar CSRF, `HttpOnly`, `Secure`, SameSite y CORS. Una sesión segura gestionada en servidor es alternativa permitida por el PDF.
- **Autorización:** RBAC (`estudiante`, `docente`, `tecnico`, `supervisor`, `administrador`) **más** control por recurso: propietario, asignación y atribuciones de supervisión. Denegar por defecto.
- **Credenciales:** hash de contraseñas con Argon2id o BCrypt correctamente configurado; no guardar texto plano ni secretos en Git.
- **Tránsito:** HTTPS/TLS entre navegador, API y conexiones gestionadas; credenciales externas por variables de entorno.
- **Entradas y salidas:** validación de DTO, paginación, límites de tamaño, manejo centralizado de excepciones, prevención de inyección y exposición mínima de datos.
- **Archivos:** detección/validación de contenido, permisos de descarga y almacenamiento privado.
- **Auditoría:** guardar actor, operación, fecha y cambio de estado; no registrar tokens, contraseñas o datos sensibles innecesarios.
- **Verificación:** pruebas unitarias de políticas, integración de endpoints, casos de acceso a recursos ajenos, SAST, DAST controlado, revisión de dependencias y principios OWASP.
- **Restricción técnica:** la seguridad nunca depende exclusivamente de ocultar botones en React.

**Matriz inicial de permisos (a formalizar en `SECURITY.md`):** solicitante crea y consulta solicitudes propias; técnico atiende únicamente las asignadas; supervisor evalúa, prioriza, asigna y cierra; administrador gestiona usuarios y catálogos. La administración no obtiene automáticamente permiso para leer información privada de todas las solicitudes si no existe necesidad aprobada.

Estos controles describen el objetivo, no capacidades de la API base actual. Formalizar permisos en `docs/SECURITY.md` al implementarlos y coordinar la sesión con frontend.

## Plan de implementación

El plan aprobado está en [`infra-uni/docs/superpowers/plans/2026-11-04-ingenieria-pendiente.md`](../infra-uni/docs/superpowers/plans/2026-11-04-ingenieria-pendiente.md). Orden de trabajo:

1. **Contratos:** `docs/api/openapi.yaml`, `docs/SECURITY.md`, `docs/DATABASE.md`, formato de errores, paginación y fechas UTC.
2. **Fundaciones:** Spring Security, Bean Validation, Flyway, springdoc, Micrometer Tracing y Testcontainers; `shared` con errores, correlación, CORS y seguridad; Actuator protegido.
3. **Migraciones:** `V1__esquema_inicial.sql`, `V2__catalogos_semilla.sql` y `V3__datos_demostracion.sql` opcional, derivadas de `DISENIO-BD.md`.
4. **Módulos:** `auth` (HU-01/HU-02), `users` y `catalogs` (HU-15 a HU-18), `requests` (HU-03 a HU-06), `management` (HU-07 a HU-11), `dashboard` (HU-12 a HU-14), auditoría/encuesta (HU-19/HU-20) y `reservations` (HU-21 a HU-25).
5. **Pruebas:** JUnit 5 + Mockito y Spring Boot Test + Testcontainers MySQL; autorización por recurso, transiciones inválidas y migraciones.
6. **Producción:** `Dockerfile` de producción, healthcheck, imágenes versionadas y workflow CI con `mvn verify`.

**Decisiones relacionadas:** [ADR-0002](../infra-uni/docs/adr/ADR-0002-autenticacion-sesion.md) (sesión), [ADR-0003](../infra-uni/docs/adr/ADR-0003-observabilidad-retencion.md) (observabilidad) y [ADR-0005](../infra-uni/docs/adr/ADR-0005-ci-cd-calidad.md) (CI/CD).

## Desarrollo, observabilidad y pruebas

Usar el entorno descrito en [DOCKER.md](../infra-uni/docs/DOCKER.md). En el contenedor backend, `mvn compile` genera clases y DevTools detecta los cambios. MySQL y los secretos de desarrollo se configuran desde infraestructura.

Actuator de salud ya está configurado. Métricas Micrometer, logs estructurados, correlación, trazas y alertas son objetivos pendientes; proteger endpoints administrativos y evitar datos sensibles en logs. Ver [ADR-0003](../infra-uni/docs/adr/ADR-0003-observabilidad-retencion.md).

Herramientas propuestas: JUnit 5 + Mockito para reglas, Spring Boot Test + Testcontainers MySQL para persistencia/API, análisis estático y de dependencias, SAST/DAST controlado y k6 para carga. Cubrir permisos por recurso, transiciones, validaciones, transacciones y migraciones. No hay suite configurada actualmente.

CI objetivo: compilación, unitarias, integración con MySQL de prueba, análisis, imagen Docker, staging y smoke tests (ver [ADR-0005](../infra-uni/docs/adr/ADR-0005-ci-cd-calidad.md)). Un `Dockerfile` de producción sigue pendiente; el actual es `Dockerfile.dev`. No afirmar resultados sin ejecutarlos y revisar su salida.
