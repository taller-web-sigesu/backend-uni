# Dashboard y reportes

> Contrato implementado; la referencia exacta es [openapi.yaml](openapi.yaml). Los ejemplos usan datos ficticios. HU-12 a HU-14.

[Convenciones de API](../API-DISENIO.md#convenciones-del-contrato) · [Diseño del backend](../BACKEND-DISENIO.md) · [Definición de indicadores](https://github.com/taller-web-sigesu/infra-uni/blob/269a0bcf7f90a68fdb16a80b177337dc6527b6fd/DISENIO-BD.md#10-dashboard-los-9-indicadores)

El supervisor usa su área por defecto y recibe `403` si pide otra. El administrador puede elegir un área o consultar todas. El indicador por responsable usa el actual o el último de una solicitud terminal. El indicador de vencimientos se muestra como no disponible antes de habilitar SLA.

## `GET /dashboard/summary`

Resume la atención de solicitudes para el periodo.

- **Seguridad.** JWT de supervisor para su área o de administrador para cualquier área.
- **Entrada.** `fechaDesde` y `fechaHasta` obligatorios, como instantes UTC. `areaId` opcional. El intervalo de registro incluye el inicio y excluye el fin.
- **Salida.** Indicadores, agrupaciones y filtros efectivos. Las horas se miden hasta la resolución y las proporciones se expresan como porcentajes.
- **Errores.** `400` por intervalo vacío o invertido y `403` por área o rol fuera del alcance.

**Petición de ejemplo**

```http
GET /api/v1/dashboard/summary?fechaDesde=2026-11-01T00:00:00Z&fechaHasta=2026-12-01T00:00:00Z&areaId=2
Authorization: Bearer <jwt-de-ejemplo>
```

**Respuesta de ejemplo**

```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "filtros": {
    "fechaDesde": "2026-11-01T00:00:00Z",
    "fechaHasta": "2026-12-01T00:00:00Z",
    "areaId": "2"
  },
  "registradas": 1,
  "pendientes": 1,
  "atendidas": 0,
  "porCategoria": [
    {
      "id": "3",
      "total": 1
    }
  ],
  "porPrioridad": [
    {
      "id": "4",
      "total": 1
    }
  ],
  "porResponsable": [
    {
      "id": null,
      "total": 1
    }
  ],
  "promedioHorasAtencion": 0,
  "porcentajeResueltas": 0,
  "vencidas": {
    "total": 0,
    "porVencer": 0,
    "disponible": true
  }
}
```

## `GET /dashboard/reports`

Exporta los datos visibles del dashboard.

- **Seguridad.** JWT de supervisor para su área o de administrador para cualquier área.
- **Entrada.** `fechaDesde` y `fechaHasta` obligatorios, como instantes UTC. `areaId` opcional. El intervalo de registro incluye el inicio y excluye el fin. `tipo` obligatorio, `solicitudes` o `indicadores`. Sin cuerpo.
- **Salida.** CSV UTF-8 descargable. Las solicitudes abarcan todos los registros autorizados que coinciden con los filtros. El CSV de solicitudes vacío conserva los encabezados. Encabezado exacto: `codigo,categoria,area,estado,prioridad,responsable,creadoEn`; `area` es el nombre del área y `responsable` el último técnico asignado (vacío si nunca se asignó). Los valores que empiezan por `=`, `+`, `-`, `@`, tabulador o retorno se prefijan con `'`.
- **Errores.** `400` por tipo o rango inválido y `403` por área o rol fuera del alcance.

**Petición de ejemplo**

```http
GET /api/v1/dashboard/reports?tipo=solicitudes&fechaDesde=2026-11-01T00:00:00Z&fechaHasta=2026-12-01T00:00:00Z&areaId=2
Authorization: Bearer <jwt-de-ejemplo>
```

**Respuesta de ejemplo**

```http
HTTP/1.1 200 OK
Content-Type: text/csv; charset=UTF-8
Content-Disposition: attachment; filename="solicitudes.csv"

codigo,categoria,area,estado,prioridad,responsable,creadoEn
SOL-2026-000031,Conectividad,Tecnologías de la Información,NUEVO,Media,,2026-11-06T15:00:00Z
```

Con `tipo=indicadores`, la descarga se llama `indicadores.csv` y devuelve una fila por métrica o grupo. Extracto del formato propuesto.

```csv
indicador,grupo,valor
registradas,,1
porCategoria,3,1
porPrioridad,4,1
```
