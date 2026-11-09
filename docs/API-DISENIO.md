# Diseño de la API de Gestal

> Propuesta para revisión del equipo. Incluye 55 operaciones de negocio y dos rutas de operación existentes en la base del backend.

[Diseño del backend](BACKEND-DISENIO.md) · [Historias de usuario](https://github.com/taller-web-sigesu/infra-uni/tree/269a0bcf7f90a68fdb16a80b177337dc6527b6fd/docs/requirements/user-stories/) · [Diseño de datos](https://github.com/taller-web-sigesu/infra-uni/blob/269a0bcf7f90a68fdb16a80b177337dc6527b6fd/DISENIO-BD.md)

## Contratos por negocio

| Documento | Cobertura | Operaciones |
| --- | --- | --- |
| [Acceso y sesión](api/autenticacion.md) | HU-01 a HU-02 | 4 |
| [Registro y seguimiento de solicitudes](api/solicitudes.md) | HU-03 a HU-06 y bandejas de HU-07 y HU-09 | 6 |
| [Asignación, prioridad y ciclo de vida](api/atencion.md) | HU-07 a HU-11 | 4 |
| [Dashboard y reportes](api/dashboard.md) | HU-12 a HU-14 | 2 |
| [Usuarios y roles](api/usuarios.md) | HU-15 | 5 |
| [Catálogos del servicio](api/catalogos.md) | HU-03, HU-16 y HU-17 | 12 |
| [SLA, auditoría y satisfacción](api/calidad-servicio.md) | HU-18 a HU-20 | 5 |
| [Reserva de ambientes](api/reservas.md) | HU-21 a HU-25 | 17 |
| [Operación y salud](api/operacion.md) | Operación transversal | 2 |

## Convenciones del contrato

- API REST con prefijo `/api/v1`. Los encabezados de operación muestran rutas relativas a ese prefijo y los ejemplos incluyen la ruta completa. Actuator se publica aparte. Se conservan los nombres de recursos en inglés que usa la arquitectura del backend. Los campos de negocio se proponen en español y en `camelCase`.
- JSON para entradas y salidas, con CSV como formato inicial propuesto para descargas. `GET` consulta, `POST` crea recursos o inicia operaciones de sesión, `PUT` sustituye un recurso completo y `PATCH` modifica campos o subrecursos como `status`. Las altas de recursos devuelven `201 Created` con `Location` cuando existe una URL individual de consulta. Un `204 No Content` lleva el cuerpo vacío.
- Listados con `page` desde 0 y `size` de 20 por defecto y máximo 100. Respuesta con `items`, `page`, `size` y `totalElements`. Los filtros y órdenes admitidos se declaran por recurso. El servidor aplica primero el alcance del usuario y usa un orden estable con `id` como desempate.
- Se propone serializar los identificadores como cadenas decimales para evitar pérdida de precisión en JavaScript. `id` identifica el recurso en la URL y `codigo` es la referencia visible de negocio. Los instantes de creación, auditoría y SLA se devuelven en UTC. Las franjas se expresan mediante `fecha` (`YYYY-MM-DD`), `horaInicio` y `horaFin` (`HH:mm`) en la zona del campus, propuesta como `America/Lima`.
- Los errores siguen un formato común `application/problem+json`, con `type`, `title`, `status`, `detail`, `codigo`, `correlationId` y, cuando corresponda, `errores` por campo. `400` indica datos inválidos, `401` una credencial ausente o inválida, `403` una operación vedada al rol y `404` un recurso inexistente o fuera del alcance de visibilidad. `409` indica un conflicto con el estado vigente, como una franja ocupada, una transición inválida o una encuesta ya registrada. `429` indica que se alcanzó un límite de frecuencia. Los fallos internos tienen mensajes genéricos.
- Cada escritura valida campos, rol y acceso al recurso. Identidad del actor, fechas de registro, códigos y estados iniciales se determinan en el servidor. Ante un `409`, el cliente vuelve a consultar el recurso antes de intentar otro cambio.

### Seguridad y ejemplos

Las operaciones de negocio usan `Authorization: Bearer <token>` salvo los flujos de autenticación que indican otra credencial. Las páginas de cada negocio detallan el rol y el alcance por recurso. Cuando se usa una cookie, el flujo aplica controles de origen y CSRF. Los secretos y datos de los ejemplos son ficticios. Las rutas y reglas derivan del diseño previo. Los nombres nuevos de campos, formas de respuesta y formatos de ejemplo son propuestas para el futuro contrato OpenAPI.

Los casos comunes de `401`, `403`, `404`, `409` y `429` siguen el mismo formato de error. Se devuelve `X-Correlation-ID` para diagnóstico. Ejemplo de un conflicto de reserva

```http
HTTP/1.1 409 Conflict
Content-Type: application/problem+json
X-Correlation-ID: demo-res-01

{
  "type": "about:blank",
  "title": "Conflicto",
  "status": 409,
  "detail": "La franja seleccionada ya está ocupada.",
  "codigo": "FRANJA_OCUPADA",
  "correlationId": "demo-res-01"
}
```

## Criterios de aceptación propuestos

| Historia | Comprobación |
| --- | --- |
| HU-13 · Filtros | Una solicitud registrada exactamente en `fechaDesde` se incluye y otra en `fechaHasta` queda fuera. Un rango invertido o vacío devuelve `400` y el supervisor recibe `403` si consulta otra área. |
| HU-14 · Exportación | El CSV de solicitudes contiene todos los registros que cumplen los filtros y el alcance del actor. Si ninguno coincide, devuelve solo los encabezados. El CSV de indicadores coincide con el resumen para el mismo conjunto de datos. |
| HU-17 · Prioridades y estados | El administrador puede actualizar una prioridad y el nombre visible de un estado. El nombre nuevo aparece en las consultas y se mantienen el código y las transiciones del estado. Un intento de editar el código devuelve `400`. |
| HU-18 · SLA | Si hay una política vigente para categoría y otra para prioridad, se aplica la de categoría. Al alcanzar el aviso y luego superar el plazo, la solicitud abierta pasa a `POR_VENCER` y `VENCIDA`, respectivamente. Modificar una política o la prioridad conserva el plazo de una solicitud existente. |
| HU-19 · Auditoría | Al cambiar una prioridad, el supervisor del área ve el campo, el valor previo, el nuevo, el autor y la fecha. Otro supervisor recibe `404` al consultar el mismo recurso. |
| HU-20 · Encuesta | El propietario registra una calificación de 1 a 5 cuando la solicitud está cerrada. Una calificación inválida devuelve `400`. Una respuesta duplicada o un envío del propietario antes del cierre devuelve `409`. |

## Cierre del contrato

Tras la aprobación, OpenAPI formalizará rutas, esquemas, filtros, formatos y límites acordados con frontend. La matriz de seguridad detallará el alcance de cada rol y el primer ingreso. [Plan de ingeniería](https://github.com/taller-web-sigesu/infra-uni/blob/269a0bcf7f90a68fdb16a80b177337dc6527b6fd/docs/superpowers/plans/2026-11-04-ingenieria-pendiente.md)
