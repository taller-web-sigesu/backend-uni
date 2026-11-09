# Catálogos del servicio

> Contrato propuesto para revisión. Los ejemplos usan datos ficticios. HU-03, HU-16 y HU-17.

[Convenciones de API](../API-DISENIO.md#convenciones-del-contrato) · [Diseño del backend](../BACKEND-DISENIO.md)

Las categorías requieren un área y una prioridad predeterminada activas al crearse o cambiar esas referencias. Las bajas son lógicas y conservan datos históricos. La configuración debe permitir completar las solicitudes en curso.

## `GET /catalogs/categories`

Lista categorías habilitadas para registrar solicitudes.

- **Seguridad.** JWT de cualquier usuario con sesión habilitada para operaciones de negocio.
- **Entrada.** `page` y `size` opcionales. Sin cuerpo.
- **Salida.** Página de categorías habilitadas con identificador y nombre visible.
- **Errores.** `400` por paginación inválida y `401` por sesión inválida.

**Petición de ejemplo**

```http
GET /api/v1/catalogs/categories
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
      "nombre": "Conectividad"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1
}
```

## `GET /admin/catalogs/areas`

Lista áreas para administración.

- **Seguridad.** JWT de administrador.
- **Entrada.** `page` y `size` opcionales. Sin cuerpo.
- **Salida.** Página con registros activos e inactivos.
- **Errores.** `400` por paginación inválida y `403` para otro rol.

**Petición de ejemplo**

```http
GET /api/v1/admin/catalogs/areas
Authorization: Bearer <jwt-de-ejemplo>
```

**Respuesta de ejemplo**

```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "items": [
    {
      "id": "2",
      "codigo": "TI",
      "nombre": "Soporte TI",
      "activo": true
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1
}
```

## `POST /admin/catalogs/areas`

Crea un registro de área.

- **Seguridad.** JWT de administrador.
- **Entrada.** JSON con `codigo` y `nombre` obligatorios, `descripcion` opcional.
- **Salida.** Registro creado y activo.
- **Errores.** `400` por campos o referencias inválidas, `409` por código o nombre duplicado y `403` para otro rol.

**Petición de ejemplo**

```http
POST /api/v1/admin/catalogs/areas
Authorization: Bearer <jwt-de-ejemplo>
Content-Type: application/json

{
  "codigo": "TI",
  "nombre": "Soporte TI"
}
```

**Respuesta de ejemplo**

```http
HTTP/1.1 201 Created
Content-Type: application/json

{
  "id": "2",
  "codigo": "TI",
  "nombre": "Soporte TI",
  "activo": true
}
```

## `PATCH /admin/catalogs/areas/{id}`

Edita los datos de área o aplica una baja lógica.

- **Seguridad.** JWT de administrador.
- **Entrada.** `id` obligatorio. JSON parcial con al menos uno de estos campos: `nombre`, `descripcion`, `activo`.
- **Salida.** Registro actualizado con código estable.
- **Errores.** `400` por campos o referencias inválidas, `404` por registro inexistente, `409` por conflicto de unicidad o dependencias activas y `403` para otro rol.

**Petición de ejemplo**

```http
PATCH /api/v1/admin/catalogs/areas/2
Authorization: Bearer <jwt-de-ejemplo>
Content-Type: application/json

{
  "nombre": "Servicios TI"
}
```

**Respuesta de ejemplo**

```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "id": "2",
  "codigo": "TI",
  "nombre": "Servicios TI",
  "activo": true
}
```

## `GET /admin/catalogs/categories`

Lista categorías para administración.

- **Seguridad.** JWT de administrador.
- **Entrada.** `page` y `size` opcionales. Sin cuerpo.
- **Salida.** Página con registros activos e inactivos.
- **Errores.** `400` por paginación inválida y `403` para otro rol.

**Petición de ejemplo**

```http
GET /api/v1/admin/catalogs/categories
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
      "codigo": "WIFI",
      "nombre": "Conectividad",
      "areaId": "2",
      "prioridadPredeterminadaId": "4",
      "activo": true
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1
}
```

## `POST /admin/catalogs/categories`

Crea un registro de categoría.

- **Seguridad.** JWT de administrador.
- **Entrada.** JSON con `codigo`, `nombre`, `areaId` y `prioridadPredeterminadaId` obligatorios, `descripcion` opcional.
- **Salida.** Registro creado y activo.
- **Errores.** `400` por campos o referencias inválidas, `409` por código o nombre duplicado y `403` para otro rol.

**Petición de ejemplo**

```http
POST /api/v1/admin/catalogs/categories
Authorization: Bearer <jwt-de-ejemplo>
Content-Type: application/json

{
  "codigo": "WIFI",
  "nombre": "Conectividad",
  "areaId": "2",
  "prioridadPredeterminadaId": "4"
}
```

**Respuesta de ejemplo**

```http
HTTP/1.1 201 Created
Content-Type: application/json

{
  "id": "3",
  "codigo": "WIFI",
  "nombre": "Conectividad",
  "areaId": "2",
  "prioridadPredeterminadaId": "4",
  "activo": true
}
```

## `PATCH /admin/catalogs/categories/{id}`

Edita los datos de categoría o aplica una baja lógica.

- **Seguridad.** JWT de administrador.
- **Entrada.** `id` obligatorio. JSON parcial con al menos uno de estos campos: `nombre`, `descripcion`, `areaId`, `prioridadPredeterminadaId`, `activo`.
- **Salida.** Registro actualizado con código estable.
- **Errores.** `400` por campos o referencias inválidas, `404` por registro inexistente, `409` por conflicto de unicidad o dependencias activas y `403` para otro rol.

**Petición de ejemplo**

```http
PATCH /api/v1/admin/catalogs/categories/3
Authorization: Bearer <jwt-de-ejemplo>
Content-Type: application/json

{
  "prioridadPredeterminadaId": "5"
}
```

**Respuesta de ejemplo**

```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "id": "3",
  "codigo": "WIFI",
  "nombre": "Conectividad",
  "areaId": "2",
  "prioridadPredeterminadaId": "5",
  "activo": true
}
```

## `GET /admin/catalogs/priorities`

Lista prioridades para administración.

- **Seguridad.** JWT de administrador.
- **Entrada.** `page` y `size` opcionales. Sin cuerpo.
- **Salida.** Página con registros activos e inactivos.
- **Errores.** `400` por paginación inválida y `403` para otro rol.

**Petición de ejemplo**

```http
GET /api/v1/admin/catalogs/priorities
Authorization: Bearer <jwt-de-ejemplo>
```

**Respuesta de ejemplo**

```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "items": [
    {
      "id": "4",
      "codigo": "MEDIA",
      "nombre": "Media",
      "nivel": 2,
      "activo": true
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1
}
```

## `POST /admin/catalogs/priorities`

Crea un registro de prioridad.

- **Seguridad.** JWT de administrador.
- **Entrada.** JSON con `codigo`, `nombre` y `nivel` obligatorios, `color` opcional.
- **Salida.** Registro creado y activo.
- **Errores.** `400` por campos o referencias inválidas, `409` por código, nombre o nivel duplicado y `403` para otro rol.

**Petición de ejemplo**

```http
POST /api/v1/admin/catalogs/priorities
Authorization: Bearer <jwt-de-ejemplo>
Content-Type: application/json

{
  "codigo": "MEDIA",
  "nombre": "Media",
  "nivel": 2
}
```

**Respuesta de ejemplo**

```http
HTTP/1.1 201 Created
Content-Type: application/json

{
  "id": "4",
  "codigo": "MEDIA",
  "nombre": "Media",
  "nivel": 2,
  "activo": true
}
```

## `PATCH /admin/catalogs/priorities/{id}`

Edita los datos de prioridad o aplica una baja lógica.

- **Seguridad.** JWT de administrador.
- **Entrada.** `id` obligatorio. JSON parcial con al menos uno de estos campos: `nombre`, `nivel`, `color`, `activo`.
- **Salida.** Registro actualizado con código estable.
- **Errores.** `400` por campos o referencias inválidas, `404` por registro inexistente, `409` por conflicto de unicidad o dependencias activas y `403` para otro rol.

**Petición de ejemplo**

```http
PATCH /api/v1/admin/catalogs/priorities/4
Authorization: Bearer <jwt-de-ejemplo>
Content-Type: application/json

{
  "nombre": "Normal"
}
```

**Respuesta de ejemplo**

```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "id": "4",
  "codigo": "MEDIA",
  "nombre": "Normal",
  "nivel": 2,
  "activo": true
}
```

## `GET /admin/catalogs/request-statuses`

Consulta estados del flujo de solicitudes.

- **Seguridad.** JWT de administrador.
- **Entrada.** Sin parámetros ni cuerpo.
- **Salida.** Catálogo finito con código estable, nombre visible y marca de estado final.
- **Errores.** `403` para otro rol.

**Petición de ejemplo**

```http
GET /api/v1/admin/catalogs/request-statuses
Authorization: Bearer <jwt-de-ejemplo>
```

**Respuesta de ejemplo**

```http
HTTP/1.1 200 OK
Content-Type: application/json

[
  {
    "id": "1",
    "codigo": "NUEVO",
    "nombre": "Nuevo",
    "esFinal": false
  }
]
```

## `PATCH /admin/catalogs/request-statuses/{id}`

Personaliza el nombre visible de un estado.

- **Seguridad.** JWT de administrador.
- **Entrada.** `id` obligatorio. JSON con `nombre` obligatorio como único campo editable.
- **Salida.** Estado con nombre visible actualizado. Su código y sus transiciones se conservan.
- **Errores.** `400` si se intenta editar el código o falta el nombre, `404` si el estado no existe y `403` para otro rol.

**Petición de ejemplo**

```http
PATCH /api/v1/admin/catalogs/request-statuses/1
Authorization: Bearer <jwt-de-ejemplo>
Content-Type: application/json

{
  "nombre": "Registrado"
}
```

**Respuesta de ejemplo**

```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "id": "1",
  "codigo": "NUEVO",
  "nombre": "Registrado",
  "esFinal": false
}
```
