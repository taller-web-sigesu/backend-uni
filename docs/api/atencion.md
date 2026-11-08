# Asignación, prioridad y ciclo de vida

> Contrato propuesto para revisión. Los ejemplos usan datos ficticios. HU-07 a HU-11.

[Convenciones de API](../API-DISENIO.md#convenciones-del-contrato) · [Diseño del backend](../BACKEND-DISENIO.md) · [Reglas de datos](https://github.com/taller-web-sigesu/infra-uni/blob/269a0bcf7f90a68fdb16a80b177337dc6527b6fd/DISENIO-BD.md#7-reglas-de-integridad-y-negocio)

La [bandeja por rol](solicitudes.md#get-requests) se documenta en Solicitudes. La primera asignación pasa de `NUEVO` a `ASIGNADO`. La reasignación conserva el estado. El técnico inicia o resuelve; el supervisor cierra desde `RESUELTO` o rechaza desde `NUEVO`, `ASIGNADO` o `EN_PROCESO`. Al cerrar o rechazar, termina la asignación vigente, se conserva el último responsable en historial y la solicitud sale de la bandeja del técnico. Ambos estados son terminales.

## `GET /requests/{id}/eligible-technicians`

Lista técnicos disponibles para asignación.

- **Seguridad.** JWT de supervisor del área de la solicitud.
- **Entrada.** `id` obligatorio. `page` y `size` opcionales.
- **Salida.** Página de técnicos activos del área de la solicitud.
- **Errores.** `404` si la solicitud está fuera del área y `403` si el rol carece del permiso.

**Petición de ejemplo**

```http
GET /api/v1/requests/31/eligible-technicians
Authorization: Bearer <jwt-de-ejemplo>
```

**Respuesta de ejemplo**

```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "items": [
    {
      "id": "19",
      "nombreCompleto": "Luis Demo",
      "areaId": "2"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1
}
```

## `POST /requests/{id}/assignments`

Asigna o reasigna la solicitud.

- **Seguridad.** JWT de supervisor del área de la solicitud.
- **Entrada.** `id` obligatorio. JSON con `tecnicoId` obligatorio y `prioridadId` opcional.
- **Salida.** Asignación vigente creada y estado resultante de la solicitud.
- **Errores.** `400` por técnico no elegible o campos inválidos, `404` por alcance y `409` si la solicitud es terminal o la escritura entra en conflicto.

**Petición de ejemplo**

```http
POST /api/v1/requests/31/assignments
Authorization: Bearer <jwt-de-ejemplo>
Content-Type: application/json

{
  "tecnicoId": "19",
  "prioridadId": "4"
}
```

**Respuesta de ejemplo**

```http
HTTP/1.1 201 Created
Content-Type: application/json

{
  "id": "9",
  "solicitudId": "31",
  "tecnicoId": "19",
  "asignadaEn": "2026-11-06T15:30:00Z",
  "estadoSolicitud": "ASIGNADO",
  "prioridadId": "4"
}
```

## `PATCH /requests/{id}`

Actualiza la prioridad de una solicitud abierta y conserva su SLA.

- **Seguridad.** JWT de supervisor del área de la solicitud.
- **Entrada.** `id` obligatorio. JSON con `prioridadId` obligatorio.
- **Salida.** Resumen de la solicitud con la prioridad nueva.
- **Errores.** `400` por prioridad inválida, `404` por alcance y `409` si la solicitud ya es terminal.

**Petición de ejemplo**

```http
PATCH /api/v1/requests/31
Authorization: Bearer <jwt-de-ejemplo>
Content-Type: application/json

{
  "prioridadId": "5"
}
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
  "prioridadId": "5",
  "estado": "ASIGNADO",
  "creadoEn": "2026-11-06T15:00:00Z",
  "responsableActual": {
    "id": "19",
    "nombreCompleto": "Luis Demo"
  }
}
```

## `PATCH /requests/{id}/status`

Cambia el estado según la transición autorizada.

- **Seguridad.** JWT de técnico asignado para iniciar o resolver, o de supervisor del área para cerrar o rechazar.
- **Entrada.** `id` obligatorio. JSON con `estado` obligatorio y `motivo` obligatorio al rechazar.
- **Salida.** Estado actualizado de la solicitud.
- **Errores.** `400` si falta el motivo requerido, `403` por rol sin permiso, `404` por alcance y `409` por transición inválida o concurrente.

**Petición de ejemplo**

```http
PATCH /api/v1/requests/31/status
Authorization: Bearer <jwt-de-ejemplo>
Content-Type: application/json

{
  "estado": "EN_PROCESO"
}
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
  "estado": "EN_PROCESO",
  "creadoEn": "2026-11-06T15:00:00Z",
  "responsableActual": {
    "id": "19",
    "nombreCompleto": "Luis Demo"
  }
}
```

**Variante de rechazo**

```json
{"estado":"RECHAZADO","motivo":"El servicio solicitado corresponde a otra sede."}
```
