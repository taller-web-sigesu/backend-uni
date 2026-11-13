# Reserva de ambientes

> Contrato implementado; la referencia exacta es [openapi.yaml](openapi.yaml). Los ejemplos usan datos ficticios. HU-21 a HU-25.

[Convenciones de API](../API-DISENIO.md#convenciones-del-contrato) · [Diseño del backend](../BACKEND-DISENIO.md) · [Reglas de reservas](https://github.com/taller-web-sigesu/infra-uni/blob/269a0bcf7f90a68fdb16a80b177337dc6527b6fd/DISENIO-BD.md#78-reservas-de-ambientes)

Las reservas pendientes y confirmadas ocupan la franja. El servidor verifica horario, anticipación, duración, capacidad, límite diario y solapamientos al registrar. La disponibilidad es informativa. El calendario es `America/Lima`. Para el piloto se usa una franja semanal por día y los feriados siguen ese horario. La aprobación siempre es obligatoria. Se proponen los valores de los datos semilla para la demo, sujetos a validación antes de operar. Los valores semilla son de 30 a 240 minutos, anticipación mínima de una hora y máxima de 30 días calendario, con dos reservas pendientes o confirmadas por solicitante y fecha entre todos los espacios.

## `GET /spaces`

Lista espacios activos y su horario para el día elegido.

- **Seguridad.** JWT de cualquier usuario con sesión habilitada para operaciones de negocio.
- **Entrada.** `tipoEspacioId`, `pabellon`, `capacidadMinima` y `fecha` opcionales. Sin fecha se toma el día actual en `America/Lima`. `page` y `size` opcionales.
- **Salida.** Página de espacios con tipo, pabellón, capacidad y horario del día.
- **Errores.** `400` por fecha, aforo o paginación inválidos.

**Petición de ejemplo**

```http
GET /api/v1/spaces?tipoEspacioId=3&pabellon=B&capacidadMinima=20&fecha=2026-11-10
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
      "nombre": "Laboratorio 204",
      "tipo": {
        "id": "3",
        "nombre": "Laboratorio"
      },
      "pabellon": "B",
      "capacidad": 24,
      "horario": {
        "horaApertura": "08:00",
        "horaCierre": "18:00"
      }
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1
}
```

## `GET /spaces/{id}/availability`

Consulta franjas de un ambiente activo.

- **Seguridad.** JWT de cualquier usuario con sesión habilitada para operaciones de negocio.
- **Entrada.** `id` obligatorio en la ruta y `fecha` obligatoria en formato `YYYY-MM-DD`. Sin cuerpo.
- **Salida.** Horario del día y franjas libres y ocupadas. No incluye solicitantes ni motivos. Si el espacio está cerrado ese día, las franjas están vacías.
- **Errores.** `400` por fecha inválida y `404` si el ambiente no existe o está inactivo.

**Petición de ejemplo**

```http
GET /api/v1/spaces/8/availability?fecha=2026-11-10
Authorization: Bearer <jwt-de-ejemplo>
```

**Respuesta de ejemplo**

```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "espacioId": "8",
  "fecha": "2026-11-10",
  "horario": {
    "horaApertura": "08:00",
    "horaCierre": "18:00"
  },
  "libres": [
    {
      "horaInicio": "08:00",
      "horaFin": "10:00"
    },
    {
      "horaInicio": "11:00",
      "horaFin": "18:00"
    }
  ],
  "ocupadas": [
    {
      "horaInicio": "10:00",
      "horaFin": "11:00"
    }
  ]
}
```

## `POST /reservations`

Registra una reserva pendiente de aprobación.

- **Seguridad.** JWT de cualquier usuario con sesión habilitada para operaciones de negocio.
- **Entrada.** JSON con `espacioId`, `fecha`, `horaInicio`, `horaFin` y `motivo` obligatorios. `numAsistentes` es opcional y debe ser positivo si se informa.
- **Salida.** Reserva en `PENDIENTE` y código generado.
- **Errores.** `400` por horario, duración, anticipación o capacidad inválidos, `404` por espacio no disponible para altas y `409` por solapamiento o límite diario alcanzado.

**Petición de ejemplo**

```http
POST /api/v1/reservations
Authorization: Bearer <jwt-de-ejemplo>
Content-Type: application/json

{
  "espacioId": "8",
  "fecha": "2026-11-10",
  "horaInicio": "10:00",
  "horaFin": "11:00",
  "motivo": "Sesión de laboratorio",
  "numAsistentes": 18
}
```

**Respuesta de ejemplo**

```http
HTTP/1.1 201 Created
Content-Type: application/json
Location: /api/v1/reservations/42

{
  "id": "42",
  "codigo": "RES-2026-000042",
  "espacioId": "8",
  "fecha": "2026-11-10",
  "horaInicio": "10:00",
  "horaFin": "11:00",
  "estado": "PENDIENTE"
}
```

## `GET /reservations`

Lista reservas propias o la bandeja de aprobación.

- **Seguridad.** JWT de cualquier usuario con sesión habilitada para operaciones de negocio. La vista de gestión se limita al supervisor del área del espacio o al administrador.
- **Entrada.** `page` y `size` opcionales. `estado`, `fechaDesde` y `fechaHasta` opcionales, con fechas locales y extremos incluidos. `vista` por defecto `propias`; `gestion` habilita la bandeja de pendientes.
- **Salida.** Página con código, espacio, fecha, franja y estado.
- **Errores.** `400` por filtro inválido y `403` si el actor no puede usar la vista de gestión.

**Petición de ejemplo**

```http
GET /api/v1/reservations?fechaDesde=2026-11-10&fechaHasta=2026-11-14&page=0&size=20
Authorization: Bearer <jwt-de-ejemplo>
```

**Respuesta de ejemplo**

```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "items": [
    {
      "id": "42",
      "codigo": "RES-2026-000042",
      "espacioId": "8",
      "fecha": "2026-11-10",
      "horaInicio": "10:00",
      "horaFin": "11:00",
      "estado": "PENDIENTE"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1
}
```

**Variante de gestión**

```http
GET /api/v1/reservations?vista=gestion&estado=PENDIENTE&page=0&size=20
Authorization: Bearer <jwt-supervisor>
```

La vista de gestión devuelve la misma estructura paginada y se limita a pendientes.

## `GET /reservations/{id}`

Consulta una reserva y su estado operativo cuando corresponda.

- **Seguridad.** JWT del propietario, del supervisor del área del espacio o de administrador. Si el espacio carece de área, la consulta de gestión corresponde al administrador.
- **Entrada.** `id` obligatorio. Sin cuerpo.
- **Salida.** Detalle con franja, motivo y asistentes cuando se informaron. Incluye el motivo de rechazo si aplica.
- **Errores.** `404` si la reserva no existe o está fuera del alcance.

**Petición de ejemplo**

```http
GET /api/v1/reservations/42
Authorization: Bearer <jwt-de-ejemplo>
```

**Respuesta de ejemplo**

```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "id": "42",
  "codigo": "RES-2026-000042",
  "espacioId": "8",
  "fecha": "2026-11-10",
  "horaInicio": "10:00",
  "horaFin": "11:00",
  "estado": "CONFIRMADA",
  "estadoOperativo": "PROXIMA",
  "solicitanteId": "12",
  "motivo": "Sesión de laboratorio",
  "numAsistentes": 18,
  "motivoRechazo": null
}
```

## `GET /reservations/{id}/history`

Consulta las transiciones de una reserva.

- **Seguridad.** JWT del propietario, del supervisor del área del espacio o de administrador. Si el espacio carece de área, la consulta de gestión corresponde al administrador.
- **Entrada.** `id` obligatorio. `page` y `size` opcionales.
- **Salida.** Página cronológica de estados con actor, fecha y motivo cuando corresponda.
- **Errores.** `404` por recurso fuera de alcance y `400` por paginación inválida.

**Petición de ejemplo**

```http
GET /api/v1/reservations/42/history
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
      "estado": "PENDIENTE",
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

## `PATCH /reservations/{id}/status`

Confirma, rechaza o cancela una reserva.

- **Seguridad.** JWT de supervisor del área o de administrador para decidir una reserva pendiente. Solo su propietario puede cancelarla antes del inicio. Se impide la autoaprobación.
- **Entrada.** `id` obligatorio. JSON con `estado` obligatorio (`CONFIRMADA`, `RECHAZADA` o `CANCELADA`). `motivo` obligatorio al rechazar.
- **Salida.** Reserva con su nuevo estado. Confirmar revalida solapamientos. Cancelar libera la franja.
- **Errores.** `400` por estado desconocido o motivo faltante, `403` por acción prohibida, `404` por reserva fuera de alcance y `409` por conflicto de franja, inicio alcanzado o transición inválida.

**Petición de ejemplo**

```http
PATCH /api/v1/reservations/42/status
Authorization: Bearer <jwt-de-ejemplo>
Content-Type: application/json

{
  "estado": "CONFIRMADA"
}
```

**Respuesta de ejemplo**

```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "id": "42",
  "codigo": "RES-2026-000042",
  "espacioId": "8",
  "fecha": "2026-11-10",
  "horaInicio": "10:00",
  "horaFin": "11:00",
  "estado": "CONFIRMADA",
  "estadoOperativo": "PROXIMA"
}
```

**Variantes del cuerpo**

```json
{"estado":"RECHAZADA","motivo":"El ambiente no está habilitado para esa actividad."}
```

```json
{"estado":"CANCELADA"}
```

## `GET /admin/spaces`

Lista espacios para administración.

- **Seguridad.** JWT de administrador.
- **Entrada.** `page` y `size` opcionales. Sin cuerpo.
- **Salida.** Página de espacios activos e inactivos.
- **Errores.** `400` por paginación inválida y `403` para otro rol.

**Petición de ejemplo**

```http
GET /api/v1/admin/spaces
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
      "codigo": "LAB-204",
      "nombre": "Laboratorio 204",
      "tipoEspacioId": "3",
      "areaId": "2",
      "pabellon": "B",
      "piso": "2",
      "capacidad": 24,
      "requiereAprobacion": true,
      "activo": true
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1
}
```

## `POST /admin/spaces`

Crea un ambiente.

- **Seguridad.** JWT de administrador.
- **Entrada.** JSON con `codigo`, `nombre`, `tipoEspacioId` y `capacidad` obligatorios. `areaId`, `pabellon`, `piso`, `descripcion` y `ubicacionDetallada` opcionales. `requiereAprobacion` es verdadero.
- **Salida.** Espacio activo con aprobación obligatoria.
- **Errores.** `400` por aforo o referencia inválida, `409` por código o nombre duplicado en el pabellón y `403` para otro rol.

**Petición de ejemplo**

```http
POST /api/v1/admin/spaces
Authorization: Bearer <jwt-de-ejemplo>
Content-Type: application/json

{
  "codigo": "LAB-204",
  "nombre": "Laboratorio 204",
  "tipoEspacioId": "3",
  "areaId": "2",
  "pabellon": "B",
  "piso": "2",
  "capacidad": 24
}
```

**Respuesta de ejemplo**

```http
HTTP/1.1 201 Created
Content-Type: application/json

{
  "id": "8",
  "codigo": "LAB-204",
  "nombre": "Laboratorio 204",
  "tipoEspacioId": "3",
  "areaId": "2",
  "pabellon": "B",
  "piso": "2",
  "capacidad": 24,
  "requiereAprobacion": true,
  "activo": true
}
```

## `PATCH /admin/spaces/{id}`

Edita o desactiva un ambiente.

- **Seguridad.** JWT de administrador.
- **Entrada.** `id` obligatorio. JSON parcial con uno o más de `nombre`, `tipoEspacioId`, `areaId`, `pabellon`, `piso`, `descripcion`, `ubicacionDetallada`, `capacidad` y `activo`.
- **Salida.** Espacio actualizado. La baja impide nuevas reservas y conserva las existentes.
- **Errores.** `400` por datos inválidos, `404` por espacio inexistente, `409` por duplicado y `403` para otro rol.

**Petición de ejemplo**

```http
PATCH /api/v1/admin/spaces/8
Authorization: Bearer <jwt-de-ejemplo>
Content-Type: application/json

{
  "activo": false
}
```

**Respuesta de ejemplo**

```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "id": "8",
  "codigo": "LAB-204",
  "nombre": "Laboratorio 204",
  "tipoEspacioId": "3",
  "areaId": "2",
  "pabellon": "B",
  "piso": "2",
  "capacidad": 24,
  "requiereAprobacion": true,
  "activo": false
}
```

## `GET /admin/space-types`

Lista tipos de ambiente.

- **Seguridad.** JWT de administrador.
- **Entrada.** `page` y `size` opcionales. Sin cuerpo.
- **Salida.** Página de tipos activos e inactivos.
- **Errores.** `400` por paginación inválida y `403` para otro rol.

**Petición de ejemplo**

```http
GET /api/v1/admin/space-types
Authorization: Bearer <jwt-de-ejemplo>
```

**Respuesta de ejemplo**

```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "items": [
    {
      "id": "3",
      "codigo": "LAB",
      "nombre": "Laboratorio",
      "activo": true
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1
}
```

## `POST /admin/space-types`

Crea un tipo de ambiente.

- **Seguridad.** JWT de administrador.
- **Entrada.** JSON con `codigo` y `nombre` obligatorios. `descripcion` opcional.
- **Salida.** Tipo creado y activo.
- **Errores.** `400` por campos inválidos, `409` por código o nombre duplicado y `403` para otro rol.

**Petición de ejemplo**

```http
POST /api/v1/admin/space-types
Authorization: Bearer <jwt-de-ejemplo>
Content-Type: application/json

{
  "codigo": "LAB",
  "nombre": "Laboratorio"
}
```

**Respuesta de ejemplo**

```http
HTTP/1.1 201 Created
Content-Type: application/json

{
  "id": "3",
  "codigo": "LAB",
  "nombre": "Laboratorio",
  "activo": true
}
```

## `PATCH /admin/space-types/{id}`

Edita o desactiva un tipo de ambiente.

- **Seguridad.** JWT de administrador.
- **Entrada.** `id` obligatorio. JSON parcial con `nombre`, `descripcion` o `activo`.
- **Salida.** Tipo actualizado con código estable.
- **Errores.** `400` por campos inválidos, `404` por tipo inexistente, `409` por duplicado y `403` para otro rol.

**Petición de ejemplo**

```http
PATCH /api/v1/admin/space-types/3
Authorization: Bearer <jwt-de-ejemplo>
Content-Type: application/json

{
  "nombre": "Laboratorio docente"
}
```

**Respuesta de ejemplo**

```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "id": "3",
  "codigo": "LAB",
  "nombre": "Laboratorio docente",
  "activo": true
}
```

## `GET /admin/spaces/{id}/schedule`

Consulta el horario semanal del ambiente.

- **Seguridad.** JWT de administrador.
- **Entrada.** `id` obligatorio. Sin cuerpo.
- **Salida.** Franjas semanales con día de la semana y horas locales.
- **Errores.** `404` por espacio inexistente y `403` para otro rol.

**Petición de ejemplo**

```http
GET /api/v1/admin/spaces/8/schedule
Authorization: Bearer <jwt-de-ejemplo>
```

**Respuesta de ejemplo**

```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "franjas": [
    {
      "diaSemana": 2,
      "horaApertura": "08:00",
      "horaCierre": "18:00"
    }
  ]
}
```

## `PUT /admin/spaces/{id}/schedule`

Sustituye el horario semanal completo.

- **Seguridad.** JWT de administrador.
- **Entrada.** `id` obligatorio. JSON con `franjas`. Cada franja requiere `diaSemana` (1 para lunes, 7 para domingo), `horaApertura` y `horaCierre`. Una franja por día. Los días omitidos quedan cerrados.
- **Salida.** Horario semanal actualizado.
- **Errores.** `400` por días repetidos o franja inválida, `404` por espacio inexistente y `403` para otro rol.

**Petición de ejemplo**

```http
PUT /api/v1/admin/spaces/8/schedule
Authorization: Bearer <jwt-de-ejemplo>
Content-Type: application/json

{
  "franjas": [
    {
      "diaSemana": 2,
      "horaApertura": "08:00",
      "horaCierre": "18:00"
    }
  ]
}
```

**Respuesta de ejemplo**

```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "franjas": [
    {
      "diaSemana": 2,
      "horaApertura": "08:00",
      "horaCierre": "18:00"
    }
  ]
}
```

## `GET /admin/reservation-policy`

Consulta la política vigente de reservas.

- **Seguridad.** JWT de administrador.
- **Entrada.** Sin parámetros ni cuerpo.
- **Salida.** Duración, anticipación y límite diario vigentes.
- **Errores.** `403` para otro rol y `404` si todavía no hay política vigente.

**Petición de ejemplo**

```http
GET /api/v1/admin/reservation-policy
Authorization: Bearer <jwt-de-ejemplo>
```

**Respuesta de ejemplo**

```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "duracionMinimaMinutos": 30,
  "duracionMaximaMinutos": 240,
  "horasAnticipacionMinima": 1,
  "diasAnticipacionMaxima": 30,
  "maxReservasActivasPorDia": 2
}
```

## `PUT /admin/reservation-policy`

Sustituye la política vigente para nuevas reservas.

- **Seguridad.** JWT de administrador.
- **Entrada.** JSON con `duracionMinimaMinutos`, `duracionMaximaMinutos`, `horasAnticipacionMinima`, `diasAnticipacionMaxima` y `maxReservasActivasPorDia`, todos obligatorios. Duración mínima positiva y máxima mayor o igual. Anticipaciones no negativas y límite diario positivo.
- **Salida.** Política vigente actualizada.
- **Errores.** `400` por valores incompatibles y `403` para otro rol.

**Petición de ejemplo**

```http
PUT /api/v1/admin/reservation-policy
Authorization: Bearer <jwt-de-ejemplo>
Content-Type: application/json

{
  "duracionMinimaMinutos": 30,
  "duracionMaximaMinutos": 240,
  "horasAnticipacionMinima": 1,
  "diasAnticipacionMaxima": 30,
  "maxReservasActivasPorDia": 3
}
```

**Respuesta de ejemplo**

```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "duracionMinimaMinutos": 30,
  "duracionMaximaMinutos": 240,
  "horasAnticipacionMinima": 1,
  "diasAnticipacionMaxima": 30,
  "maxReservasActivasPorDia": 3
}
```
