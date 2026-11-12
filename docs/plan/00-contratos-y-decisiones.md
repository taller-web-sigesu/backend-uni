# Fase 00 · Contratos y decisiones del backend de Gestal

**Objetivo.** Dejar acordados el contrato HTTP, los permisos y el esquema antes de implementar las HU-01 a HU-25.

**Arquitectura y stack.** Monolito modular Java 21, Spring Boot, Maven y MySQL 8.4. El backend es dueño de la autorización, las transacciones y el contrato HTTP. El repositorio parte de un controlador de estado y configuración de JPA, datasource y Actuator.

**Estado.** Plan de trabajo pendiente. Los pasos y comandos de estos documentos describen la futura implementación.

## Secuencia y alcance de entrega

| Fase | Entregable verificable |
| --- | --- |
| Fase 00 · Contratos y decisiones | OpenAPI, matriz de permisos y esquema reconciliados |
| [Fase 01 · Fundaciones y sesión](01-fundaciones-y-sesion.md) | Base migrada y sesión revocable, HU-01 y HU-02 |
| [Fase 02 · Solicitudes, atención y reportes](02-solicitudes-atencion-y-reportes.md) | Gestión del servicio, HU-03 a HU-20 |
| [Fase 03 · Reservas de ambientes](03-reservas-de-ambientes.md) | Reservas y administración de espacios, HU-21 a HU-25 |
| [Fase 04 · Integración local y CI](04-integracion-local-y-ci.md) | Compose, imagen y verificaciones en GitHub Actions |

La entrega termina en **Docker local y CI**. Staging, producción, proveedores, backups en la nube y extensiones fuera de las HU vigentes pertenecen a entregas posteriores.

## Fuentes

- Alcance, roles y reglas de negocio. [Historias de usuario](../../../infra-uni/docs/requirements/user-stories/) · [Plan general de ingeniería](../../../infra-uni/docs/superpowers/plans/2026-11-04-ingenieria-pendiente.md)
- Contratos, responsabilidades y ejemplos. [Diseño REST](../API-DISENIO.md) · [Diseño backend](../BACKEND-DISENIO.md) · [Arquitectura backend](../../ARCHITECTURE.md) · [Diseño de Postman](../postman-disenio/postman-api-design.md)
- Persistencia y decisiones de equipo. [Diseño de datos](../../../infra-uni/DISENIO-BD.md) · [ADR](../../../infra-uni/docs/adr/)

Usar las HU para alcance y permisos, el diseño REST para interfaces, el diseño backend para separación de módulos y el diseño de datos para DDL y consultas. Registrar las discrepancias en la revisión de contrato y vincular la decisión a la HU afectada. Los ADR marcados como «Propuesto» requieren revisión para los puntos que afectan esta entrega.

## Tareas

- [ ] **Inventariar la superficie HTTP.** Crear `docs/api/openapi.yaml` con las 55 operaciones de negocio y dos rutas operativas del diseño. Para cada operación trasladar método, ruta, parámetros, cuerpo, ejemplos de respuesta, errores y requisitos de seguridad desde su documento de negocio. Comparar por método y ruta con la colección Postman, separando variantes de una misma operación. Mantener el endpoint de Actuator fuera del prefijo de negocio.
- [ ] **Cerrar convenciones comunes.** Formalizar el prefijo `/api/v1`, IDs como cadenas decimales y paginación desde cero con tamaño 20 por defecto y 100 como máximo. Aplicar primero alcance y filtros, luego ordenar con desempate estable por ID. Usar UTC para instantes y `America/Lima` para fecha y horas de reserva. Documentar `application/problem+json`, `X-Correlation-ID` y el uso de 400 para entradas inválidas, 401 para credenciales inválidas, 403 para operación vedada al rol, 404 para recursos inexistentes o invisibles, 409 para conflictos y 429 para límites de frecuencia.
- [ ] **Formalizar autorización.** Crear `docs/SECURITY.md` con una fila por operación que indique credencial, rol, alcance por propietario, asignación o área, y restricciones por estado. Registrar el primer ingreso y el flujo de cookies junto con la configuración local. Incluir casos negativos por rol y recurso.
- [ ] **Reconciliar el esquema.** Crear `docs/DATABASE.md` con tablas, vistas, convenciones, restricciones y las fuentes del DDL. Documentar los hashes de refresh consumidos, la proyección del último responsable y la restricción de franjas activas. Revisar el impacto de los cambios sobre los ejemplos y el contrato antes de escribir migraciones.
- [ ] **Validar el entorno base.** Comprobar que las versiones de Java, Spring Boot y MySQL declaradas en Maven y Compose se pueden resolver y ejecutar. Registrar las versiones usadas y la salida. Si hay una incompatibilidad, acordar el cambio de versión antes de actualizar el manifiesto.

## Decisiones de coherencia

| Tema | Resolución para la implementación | Evidencia de aceptación |
| --- | --- | --- |
| Logout | **Confirmado.** Revocar al instante la sesión actual. Comprobar su vigencia en cada petición protegida con JWT. | La siguiente petición con ese JWT y el refresh de esa sesión devuelve 401. |
| Refresh reutilizado | Conservar los hashes consumidos hasta la expiración absoluta y vincularlos a la sesión. Confirmar la revocación aunque la respuesta sea 401. | Un refresh consumido identifica y revoca la sesión que lo emitió. |
| Cancelación | **Confirmado.** Solo el propietario cancela una reserva pendiente o confirmada antes del inicio. | Un administrador que no sea propietario no puede cancelar. |
| Solicitud terminal | Terminar la asignación vigente al cerrar o rechazar y conservar el último responsable para detalle autorizado y reportes. | La solicitud sale de la bandeja activa y se atribuye al último técnico en el reporte. |
| Franjas activas | Restringir duplicados de franja activa a pendientes y confirmadas y comprobar cruces parciales en transacción. | Tras liberar una franja puede volver a reservarse y cancelarse. |

**Cierre.** El equipo revisa OpenAPI, seguridad y esquema. Cada operación tiene una HU o justificación operativa, permisos comprobables y ejemplos compatibles con su contrato. La fase 01 empieza con los contratos de fundaciones y sesión revisados.
