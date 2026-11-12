# Registro y seguimiento de solicitudes

> Contrato propuesto para revisión. Los ejemplos usan datos ficticios. HU-03 a HU-06 y bandejas de HU-07 y HU-09.

[Convenciones de API](../API-DISENIO.md#convenciones-del-contrato) · [Diseño del backend](../BACKEND-DISENIO.md)

## `POST /requests`

Registra una solicitud de servicio.

- **Seguridad.** JWT de estudiante o docente.
- **Entrada.** JSON con `categoriaId`, `ubicacion` y `descripcion`, todos obligatorios. La categoría, su área y la prioridad predeterminada deben estar activas.
- **Salida.** Solicitud en `NUEVO`, código generado, área, prioridad y fecha de registro.
- **Errores.** `400` por campos vacíos o categoría no habilitada y `403` por rol no autorizado.

**Petición de ejemplo**

```http
POST /api/v1/requests
Authorization: Bearer <jwt-de-ejemplo>
Content-Type: application/json

{
  "categoriaId": "3",
  "ubicacion": "Pabellón B, aula 204",
  "descripcion": "El aula no tiene conexión Wi-Fi."
}
```

**Respuesta de ejemplo**

```http
HTTP/1.1 201 Created
Content-Type: application/json
Location: /api/v1/requests/31

{
  "id": "31",
  "codigo": "SOL-2026-000031",
  "categoriaId": "3",
  "areaId": "2",
  "prioridadId": "4",
  "estado": "NUEVO",
  "creadoEn": "2026-11-06T15:00:00Z"
}
```

## `GET /requests`

Lista solicitudes visibles según el rol autenticado.

- **Seguridad.** JWT de estudiante, docente, técnico o supervisor. El servidor determina el alcance por propietario, asignación o área.
- **Entrada.** `page` y `size` opcionales. Para el supervisor, `estado`, `categoriaId` y `prioridadId` opcionales. Sin cuerpo.
- **Salida.** Página de resúmenes con código, categoría, prioridad, estado y fecha.
- **Errores.** `400` por filtros o paginación inválidos y `403` para roles fuera del flujo.

**Petición de ejemplo**

```http
GET /api/v1/requests?page=0&size=20
Authorization: Bearer <jwt-de-ejemplo>
```

**Respuesta de ejemplo**

```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "items": [
    {
      "id": "31",
      "codigo": "SOL-2026-000031",
      "categoriaId": "3",
      "areaId": "2",
      "prioridadId": "4",
      "estado": "NUEVO",
      "creadoEn": "2026-11-06T15:00:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1
}
```

**Ejemplos de consulta por rol**

```http
GET /api/v1/requests?page=0&size=20
Authorization: Bearer <jwt-estudiante>

GET /api/v1/requests?page=0&size=20
Authorization: Bearer <jwt-tecnico>

GET /api/v1/requests?estado=NUEVO&categoriaId=3&prioridadId=4&page=0&size=20
Authorization: Bearer <jwt-supervisor>
```

El estudiante o docente recibe registros propios. El técnico recibe asignaciones vigentes, ordenadas por prioridad descendente y fecha ascendente. El supervisor recibe solicitudes de su área y puede usar los filtros mostrados. Las tres variantes devuelven la misma estructura paginada.

## `GET /requests/{id}`

Consulta el detalle y el SLA de una solicitud.

- **Seguridad.** JWT. Solicitante propietario (estudiante o docente), técnico con asignación vigente o supervisor del área.
- **Entrada.** `id` obligatorio en la ruta. Sin cuerpo.
- **Salida.** Datos del solicitante, ubicación, descripción, área, prioridad, estado, responsable actual y, en solicitudes terminales, último responsable si existió.
- **Errores.** `404` si no existe o queda fuera del alcance del actor.

**Petición de ejemplo**

```http
GET /api/v1/requests/31
Authorization: Bearer <jwt-de-ejemplo>
```

**Respuesta de ejemplo**

```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "id": "31",
  "codigo": "SOL-2026-000031",
  "categoriaId": "3",
  "areaId": "2",
  "prioridadId": "4",
  "estado": "NUEVO",
  "creadoEn": "2026-11-06T15:00:00Z",
  "solicitante": {
    "id": "12",
    "nombreCompleto": "Ana Demo"
  },
  "ubicacion": "Pabellón B, aula 204",
  "descripcion": "El aula no tiene conexión Wi-Fi.",
  "responsableActual": null,
  "ultimoResponsable": null,
  "sla": {
    "estado": "EN_PLAZO",
    "fechaAviso": "2026-11-07T11:00:00Z",
    "fechaLimite": "2026-11-07T15:00:00Z"
  }
}
```

## `GET /requests/{id}/history`

Consulta las transiciones de la solicitud.

- **Seguridad.** JWT. Solicitante propietario (estudiante o docente), técnico con asignación vigente o supervisor del área.
- **Entrada.** `id` obligatorio. `page` y `size` opcionales.
- **Salida.** Página en orden cronológico con estado previo y nuevo, actor, fecha y motivo cuando aplique.
- **Errores.** `404` si la solicitud está fuera de alcance y `400` por paginación inválida.

**Petición de ejemplo**

```http
GET /api/v1/requests/31/history
Authorization: Bearer <jwt-de-ejemplo>
```

**Respuesta de ejemplo**

```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "items": [
    {
      "estadoAnterior": null,
      "estado": "NUEVO",
      "actorId": "12",
      "fecha": "2026-11-06T15:00:00Z",
      "motivo": null
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1
}
```

## `GET /requests/{id}/comments`

Consulta los comentarios de seguimiento.

- **Seguridad.** JWT. Solicitante propietario (estudiante o docente), técnico con asignación vigente o supervisor del área.
- **Entrada.** `id` obligatorio. `page` y `size` opcionales.
- **Salida.** Página de comentarios con autor y fecha.
- **Errores.** `404` si la solicitud está fuera de alcance y `400` por paginación inválida.

**Petición de ejemplo**

```http
GET /api/v1/requests/31/comments
Authorization: Bearer <jwt-de-ejemplo>
```

**Respuesta de ejemplo**

```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "items": [
    {
      "id": "8",
      "contenido": "El problema continúa.",
      "autor": {
        "id": "12",
        "nombreCompleto": "Ana Demo"
      },
      "creadoEn": "2026-11-06T15:20:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1
}
```

## `POST /requests/{id}/comments`

Agrega un comentario de seguimiento.

- **Seguridad.** JWT. Solicitante propietario (estudiante o docente), técnico con asignación vigente o supervisor del área.
- **Entrada.** `id` obligatorio. JSON con `contenido` no vacío.
- **Salida.** Comentario creado con su autor y fecha de servidor.
- **Errores.** `400` por contenido vacío y `404` si la solicitud está fuera de alcance.

**Petición de ejemplo**

```http
POST /api/v1/requests/31/comments
Authorization: Bearer <jwt-de-ejemplo>
Content-Type: application/json

{
  "contenido": "El problema continúa."
}
```

**Respuesta de ejemplo**

```http
HTTP/1.1 201 Created
Content-Type: application/json

{
  "id": "8",
  "contenido": "El problema continúa.",
  "autor": {
    "id": "12",
    "nombreCompleto": "Ana Demo"
  },
  "creadoEn": "2026-11-06T15:20:00Z"
}
```
