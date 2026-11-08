# Usuarios y roles

> Contrato propuesto para revisión. Los ejemplos usan datos ficticios. HU-15.

[Convenciones de API](../API-DISENIO.md#convenciones-del-contrato) · [Diseño del backend](../BACKEND-DISENIO.md)

El primer administrador se aprovisiona mediante un procedimiento controlado. Las cuentas nuevas deben cambiar su contraseña al iniciar sesión. Las respuestas excluyen contraseñas y hashes. Desactivar una cuenta revoca sus sesiones.

## `GET /admin/users`

Lista cuentas administrativas.

- **Seguridad.** JWT de administrador.
- **Entrada.** `page` y `size` opcionales. Sin cuerpo.
- **Salida.** Página con identidad, rol, área y vigencia de cada cuenta.
- **Errores.** `400` por paginación inválida y `403` para otro rol.

**Petición de ejemplo**

```http
GET /api/v1/admin/users
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
      "correo": "luis@universidad.example",
      "rol": "TECNICO",
      "areaId": "2",
      "activo": true
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1
}
```

## `GET /admin/users/{id}`

Consulta una cuenta.

- **Seguridad.** JWT de administrador.
- **Entrada.** `id` obligatorio. Sin cuerpo.
- **Salida.** Datos de perfil, rol, área y estado de la cuenta.
- **Errores.** `404` si la cuenta no existe y `403` para otro rol.

**Petición de ejemplo**

```http
GET /api/v1/admin/users/19
Authorization: Bearer <jwt-de-ejemplo>
```

**Respuesta de ejemplo**

```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "id": "19",
  "nombreCompleto": "Luis Demo",
  "correo": "luis@universidad.example",
  "rol": "TECNICO",
  "areaId": "2",
  "activo": true
}
```

## `POST /admin/users`

Crea una cuenta institucional.

- **Seguridad.** JWT de administrador.
- **Entrada.** JSON con `nombreCompleto`, `correo`, `contrasenaInicial` y `rol` obligatorios. `areaId` obligatorio para `TECNICO` y `SUPERVISOR`. `telefono` opcional.
- **Salida.** Cuenta activa con cambio de contraseña obligatorio en el primer ingreso.
- **Errores.** `400` por campos inválidos, `409` por correo duplicado y `403` para otro rol.

**Petición de ejemplo**

```http
POST /api/v1/admin/users
Authorization: Bearer <jwt-de-ejemplo>
Content-Type: application/json

{
  "nombreCompleto": "Luis Demo",
  "correo": "luis@universidad.example",
  "contrasenaInicial": "<contrasena-inicial>",
  "rol": "TECNICO",
  "areaId": "2"
}
```

**Respuesta de ejemplo**

```http
HTTP/1.1 201 Created
Content-Type: application/json
Location: /api/v1/admin/users/19

{
  "id": "19",
  "nombreCompleto": "Luis Demo",
  "correo": "luis@universidad.example",
  "rol": "TECNICO",
  "areaId": "2",
  "activo": true
}
```

## `PATCH /admin/users/{id}`

Edita datos, rol o área de una cuenta, o la desactiva.

- **Seguridad.** JWT de administrador.
- **Entrada.** `id` obligatorio. JSON parcial con uno o más de `nombreCompleto`, `correo`, `telefono`, `rol`, `areaId` y `activo`. El rol resultante determina si el área es obligatoria.
- **Salida.** Perfil actualizado. `activo: false` aplica baja lógica y revoca sus sesiones.
- **Errores.** `400` por combinación de rol y área inválida, `404` por cuenta inexistente, `409` por correo duplicado y `403` para otro rol.

**Petición de ejemplo**

```http
PATCH /api/v1/admin/users/19
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
  "id": "19",
  "nombreCompleto": "Luis Demo",
  "correo": "luis@universidad.example",
  "rol": "TECNICO",
  "areaId": "2",
  "activo": false
}
```

## `GET /admin/roles`

Consulta los roles disponibles para asignar cuentas.

- **Seguridad.** JWT de administrador.
- **Entrada.** Sin parámetros ni cuerpo.
- **Salida.** Catálogo finito de roles y nombres visibles.
- **Errores.** `403` para otro rol.

**Petición de ejemplo**

```http
GET /api/v1/admin/roles
Authorization: Bearer <jwt-de-ejemplo>
```

**Respuesta de ejemplo**

```http
HTTP/1.1 200 OK
Content-Type: application/json

[
  {
    "codigo": "ESTUDIANTE",
    "nombre": "Estudiante"
  },
  {
    "codigo": "TECNICO",
    "nombre": "Técnico"
  },
  {
    "codigo": "SUPERVISOR",
    "nombre": "Supervisor"
  },
  {
    "codigo": "ADMINISTRADOR",
    "nombre": "Administrador"
  }
]
```
