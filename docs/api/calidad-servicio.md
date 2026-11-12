# SLA, auditoría y satisfacción

> Contrato propuesto para revisión. Los ejemplos usan datos ficticios. HU-18 a HU-20.

[Convenciones de API](../API-DISENIO.md#convenciones-del-contrato) · [Diseño del backend](../BACKEND-DISENIO.md)

La política vigente por categoría prevalece sobre la de prioridad. Se congela al registrar la solicitud. Los cambios de prioridad o de política conservan los plazos originales. Historial de estados y auditoría tienen alcances distintos.

## `GET /admin/sla-policies`

Consulta las políticas de SLA.

- **Seguridad.** JWT de administrador.
- **Entrada.** `page` y `size` opcionales. Sin cuerpo.
- **Salida.** Página de políticas con alcance, umbrales y vigencia.
- **Errores.** `400` por paginación inválida y `403` para otro rol.

**Petición de ejemplo**

```http
GET /api/v1/admin/sla-policies
Authorization: Bearer <jwt-de-ejemplo>
```

**Respuesta de ejemplo**

```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "items": [
    {
      "id": "5",
      "categoriaId": "3",
      "prioridadId": null,
      "horasObjetivo": 24,
      "horasAviso": 4,
      "vigenteDesde": "2026-11-06T15:00:00Z",
      "vigenteHasta": null,
      "activo": true
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1
}
```

## `POST /admin/sla-policies`

Define una política para solicitudes nuevas.

- **Seguridad.** JWT de administrador.
- **Entrada.** JSON con exactamente uno de `categoriaId` o `prioridadId`, más `horasObjetivo` y `horasAviso`. `vigenteDesde` opcional, con la hora actual como valor inicial. `vigenteHasta` opcional.
- **Salida.** Política registrada con su vigencia.
- **Errores.** `400` por alcance o plazos inválidos, `409` si se superpone con otra política activa del mismo alcance y `403` para otro rol.

**Petición de ejemplo**

```http
POST /api/v1/admin/sla-policies
Authorization: Bearer <jwt-de-ejemplo>
Content-Type: application/json

{
  "categoriaId": "3",
  "horasObjetivo": 24,
  "horasAviso": 4,
  "vigenteDesde": "2026-11-06T15:00:00Z"
}
```

**Respuesta de ejemplo**

```http
HTTP/1.1 201 Created
Content-Type: application/json

{
  "id": "5",
  "categoriaId": "3",
  "prioridadId": null,
  "horasObjetivo": 24,
  "horasAviso": 4,
  "vigenteDesde": "2026-11-06T15:00:00Z",
  "vigenteHasta": null,
  "activo": true
}
```

## `PATCH /admin/sla-policies/{id}`

Ajusta o desactiva una política para solicitudes nuevas.

- **Seguridad.** JWT de administrador.
- **Entrada.** `id` obligatorio. JSON parcial con `horasObjetivo`, `horasAviso`, `vigenteDesde`, `vigenteHasta` o `activo`. Se conserva el alcance original.
- **Salida.** Política actualizada y plazos de solicitudes existentes conservados.
- **Errores.** `400` por plazos inválidos, `404` por política inexistente, `409` por vigencias activas superpuestas y `403` para otro rol.

**Petición de ejemplo**

```http
PATCH /api/v1/admin/sla-policies/5
Authorization: Bearer <jwt-de-ejemplo>
Content-Type: application/json

{
  "horasObjetivo": 36
}
```

**Respuesta de ejemplo**

```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "id": "5",
  "categoriaId": "3",
  "prioridadId": null,
  "horasObjetivo": 36,
  "horasAviso": 4,
  "vigenteDesde": "2026-11-06T15:00:00Z",
  "vigenteHasta": null,
  "activo": true
}
```

## `GET /requests/{id}/audit-events`

Consulta las modificaciones auditadas.

- **Seguridad.** JWT del supervisor del área de la solicitud.
- **Entrada.** `id` obligatorio. `page` y `size` opcionales.
- **Salida.** Página de eventos con campo, valor previo, valor nuevo, autor y fecha.
- **Errores.** `404` si la solicitud está fuera de alcance y `403` para otro rol.

**Petición de ejemplo**

```http
GET /api/v1/requests/31/audit-events
Authorization: Bearer <jwt-de-ejemplo>
```

**Respuesta de ejemplo**

```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "items": [
    {
      "id": "114",
      "campo": "prioridad",
      "valorAnterior": "MEDIA",
      "valorNuevo": "ALTA",
      "actorId": "25",
      "fecha": "2026-11-06T15:40:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1
}
```

## `POST /requests/{id}/survey`

Registra la satisfacción del solicitante.

- **Seguridad.** JWT del solicitante propietario (estudiante o docente).
- **Entrada.** `id` obligatorio. JSON con `calificacion` entera de 1 a 5 y `comentario` opcional.
- **Salida.** Encuesta registrada para una solicitud `CERRADO`.
- **Errores.** `400` por calificación inválida, `404` si la solicitud está fuera de alcance y `409` si la solicitud aún no está cerrada o ya existe una respuesta.

**Petición de ejemplo**

```http
POST /api/v1/requests/31/survey
Authorization: Bearer <jwt-de-ejemplo>
Content-Type: application/json

{
  "calificacion": 5,
  "comentario": "Atención oportuna."
}
```

**Respuesta de ejemplo**

```http
HTTP/1.1 201 Created
Content-Type: application/json

{
  "id": "61",
  "solicitudId": "31",
  "calificacion": 5,
  "comentario": "Atención oportuna."
}
```
