# Registro y seguimiento de solicitudes

> Contrato implementado; la referencia exacta es [openapi.yaml](openapi.yaml). Los ejemplos usan datos ficticios. HU-03 a HU-06, evidencias de HU-26 y bandejas de HU-07 y HU-09.

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
- **Errores.** `400` por filtros o paginación inválidos y `403` para roles fuera del flujo. El administrador recibe `403` en todas las rutas `/requests/**`: consulta las solicitudes mediante el [dashboard y la exportación CSV](dashboard.md).

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


## Evidencias (HU-26)

Archivos JPG, PNG o PDF asociados a una solicitud. Ver y descargar exige poder ver la solicitud (misma regla que el detalle); fuera de alcance se responde `404` sin revelar si la evidencia existe. El administrador recibe `403`. Al registrar una solicitud con evidencias, el cliente primero crea la solicitud y luego sube cada archivo.

### `POST /requests/{id}/attachments`

- **Seguridad.** JWT del solicitante, del técnico con asignación vigente o del supervisor del área. La solicitud no debe estar `CERRADO` ni `RECHAZADO`.
- **Entrada.** `multipart/form-data` con un archivo en el campo `archivo`. El tipo se detecta por el contenido (firmas de JPEG, PNG y PDF), no por la extensión ni por el `Content-Type`: un PDF enviado como `.png` se guarda como `application/pdf`. Tamaño de 1 byte a 5 MB.
- **Salida.** `201` con `Location` hacia la descarga y `{id, nombre, tamanoBytes, tipoMime, autorId, autorNombre, creadoEn, puedeEliminar}`. `nombre` es el nombre original sin ruta ni caracteres de control; nunca se usa para guardar el archivo.
- **Errores.** `400` con `errores[0].campo = "archivo"` y el mensaje «Solo se aceptan archivos JPG, PNG o PDF.», «El tamaño máximo es 5 MB.» o «El archivo está vacío.»; `404` por alcance; `409 LIMITE_EVIDENCIAS` con 5 evidencias vigentes; `409 SOLICITUD_FINALIZADA` si la solicitud está cerrada o rechazada.

```http
HTTP/1.1 201 Created
Location: /api/v1/requests/31/attachments/7/content
Content-Type: application/json

{
  "id": "7",
  "nombre": "foto-aula.jpg",
  "tamanoBytes": 2097152,
  "tipoMime": "image/jpeg",
  "autorId": "12",
  "autorNombre": "Ana Demo",
  "creadoEn": "2026-11-13T15:00:00Z",
  "puedeEliminar": true
}
```

### `GET /requests/{id}/attachments`

Lista las evidencias vigentes, de la más antigua a la más reciente. `puedeEliminar` es verdadero solo para el autor con la solicitud abierta. Sin paginación (máximo 5).

### `GET /requests/{id}/attachments/{attachmentId}/content`

Descarga el archivo con `Content-Disposition: attachment; filename*=UTF-8''…`, `X-Content-Type-Options: nosniff` y `Cache-Control: no-store`. Con Supabase Storage responde `302` a una URL firmada que vence en 5 minutos. Funciona también con la solicitud cerrada o rechazada.

### `DELETE /requests/{id}/attachments/{attachmentId}`

Solo el autor, con la solicitud abierta. Responde `204`; la evidencia sale del listado (borrado lógico con `eliminado_en` y `eliminado_por_id`), se registra `ELIMINAR_ADJUNTO` en la auditoría y el archivo se borra del almacenamiento. Errores: `403 SOLO_EL_AUTOR`, `404` y `409 SOLICITUD_FINALIZADA`.
