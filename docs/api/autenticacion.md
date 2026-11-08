# Acceso y sesión

> Contrato propuesto para revisión. Los ejemplos usan datos ficticios. HU-01 a HU-02.

[Convenciones de API](../API-DISENIO.md#convenciones-del-contrato) · [Diseño del backend](../BACKEND-DISENIO.md) · [ADR de sesión](https://github.com/taller-web-sigesu/infra-uni/blob/269a0bcf7f90a68fdb16a80b177337dc6527b6fd/docs/adr/ADR-0002-autenticacion-sesion.md)

La sesión de primer ingreso permite cambiar la contraseña, renovarse o cerrarse. En operaciones de negocio devuelve `403 CAMBIO_CONTRASENA_REQUERIDO`. El JWT se mantiene en memoria y dura 15 minutos. El refresh tiene siete días de vigencia absoluta. Se propone `SameSite=Lax` bajo el mismo sitio y `Secure` en HTTPS. Los nombres de la cookie y del objeto de respuesta son propuestas. El mecanismo concreto de CSRF por entorno se formalizará en la revisión de seguridad.

## `POST /auth/login`

Inicia sesión con la cuenta institucional creada por un administrador.

- **Seguridad.** Público, con límite de intentos. Se valida el origen en el flujo del navegador.
- **Entrada.** JSON con `correo` y `contrasena`, ambos obligatorios.
- **Salida.** JWT de acceso, usuario, indicador de cambio de contraseña y cookie de renovación.
- **Errores.** `400` si faltan campos, `401` por credenciales inválidas o cuenta inactiva y `429` por exceso de intentos.

**Petición de ejemplo**

```http
POST /api/v1/auth/login
Content-Type: application/json
Origin: https://app.gestal.example

{
  "correo": "ana@universidad.example",
  "contrasena": "<contrasena-inicial>"
}
```

**Respuesta de ejemplo**

```http
HTTP/1.1 200 OK
Content-Type: application/json
Set-Cookie: refresh_token=<refresh-de-ejemplo>; HttpOnly; Secure; SameSite=Lax; Path=/api/v1/auth
Cache-Control: no-store

{
  "tokenAcceso": "<jwt-de-ejemplo>",
  "expiraEnSegundos": 900,
  "usuario": {
    "id": "12",
    "nombreCompleto": "Ana Demo",
    "rol": "ESTUDIANTE",
    "areaId": null
  },
  "debeCambiarContrasena": true
}
```

## `POST /auth/refresh`

Rota el token de renovación y conserva el alcance de la sesión.

- **Seguridad.** Cookie de renovación y controles de origen y CSRF del despliegue.
- **Entrada.** Sin cuerpo ni parámetros. Se envía la cookie `refresh_token`.
- **Salida.** JWT nuevo, datos de la sesión y cookie rotada. Una sesión de primer ingreso sigue restringida.
- **Errores.** `401` si la cookie falta, expiró, fue revocada o el token se reutilizó. El reúso revoca la sesión.

**Petición de ejemplo**

```http
POST /api/v1/auth/refresh
Cookie: refresh_token=<refresh-de-ejemplo>
Origin: https://app.gestal.example
```

**Respuesta de ejemplo**

```http
HTTP/1.1 200 OK
Content-Type: application/json
Set-Cookie: refresh_token=<refresh-rotado>; HttpOnly; Secure; SameSite=Lax; Path=/api/v1/auth
Cache-Control: no-store

{
  "tokenAcceso": "<jwt-renovado>",
  "expiraEnSegundos": 900,
  "usuario": {
    "id": "12",
    "nombreCompleto": "Ana Demo",
    "rol": "ESTUDIANTE",
    "areaId": null
  },
  "debeCambiarContrasena": true
}
```

## `POST /auth/logout`

Revoca la sesión actual.

- **Seguridad.** JWT de esa sesión o cookie de renovación. La variante con cookie aplica los controles CSRF.
- **Entrada.** Sin cuerpo ni parámetros. El ejemplo usa JWT.
- **Salida.** `204` y eliminación de la cookie. El JWT y el refresh anteriores pierden acceso.
- **Errores.** `401` si no se presenta una credencial de sesión válida.

**Petición de ejemplo**

```http
POST /api/v1/auth/logout
Authorization: Bearer <jwt-de-ejemplo>
```

**Respuesta de ejemplo**

```http
HTTP/1.1 204 No Content
Set-Cookie: refresh_token=; Max-Age=0; HttpOnly; Secure; SameSite=Lax; Path=/api/v1/auth
Cache-Control: no-store
```

## `PATCH /auth/password`

Cambia la contraseña propia y reemplaza las credenciales de sesión.

- **Seguridad.** JWT del titular, incluido el de primer ingreso.
- **Entrada.** JSON con `contrasenaActual` y `contrasenaNueva`, ambas obligatorias.
- **Salida.** JWT y cookie de una nueva sesión. Se revocan las sesiones anteriores.
- **Errores.** `400` por datos o contraseña nueva inválidos, `401` si falla la autenticación.

**Petición de ejemplo**

```http
PATCH /api/v1/auth/password
Authorization: Bearer <jwt-de-ejemplo>
Content-Type: application/json

{
  "contrasenaActual": "<contrasena-inicial>",
  "contrasenaNueva": "<contrasena-nueva>"
}
```

**Respuesta de ejemplo**

```http
HTTP/1.1 200 OK
Content-Type: application/json
Set-Cookie: refresh_token=<refresh-de-nueva-sesion>; HttpOnly; Secure; SameSite=Lax; Path=/api/v1/auth
Cache-Control: no-store

{
  "tokenAcceso": "<jwt-de-nueva-sesion>",
  "expiraEnSegundos": 900,
  "usuario": {
    "id": "12",
    "nombreCompleto": "Ana Demo",
    "rol": "ESTUDIANTE",
    "areaId": null
  },
  "debeCambiarContrasena": false
}
```
