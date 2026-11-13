# Acceso y sesión

> Contrato propuesto para revisión. Los ejemplos usan datos ficticios. HU-01, HU-02 y HU-27.

[Convenciones de API](../API-DISENIO.md#convenciones-del-contrato) · [Diseño del backend](../BACKEND-DISENIO.md) · [ADR de sesión](https://github.com/taller-web-sigesu/infra-uni/blob/269a0bcf7f90a68fdb16a80b177337dc6527b6fd/docs/adr/ADR-0002-autenticacion-sesion.md)

La sesión de primer ingreso permite cambiar la contraseña, renovarse o cerrarse. En operaciones de negocio devuelve `403 CAMBIO_CONTRASENA_REQUERIDO`. El JWT se mantiene en memoria y dura 15 minutos. El refresh tiene siete días de vigencia absoluta. Se propone `SameSite=Lax` bajo el mismo sitio y `Secure` en HTTPS. Los nombres de la cookie y del objeto de respuesta son propuestas. El mecanismo concreto de CSRF por entorno se formalizará en la revisión de seguridad.

## `POST /auth/register`

Registra una cuenta de estudiante con el correo institucional (HU-27). La cuenta queda pendiente hasta que un administrador la apruebe ([HU-28](usuarios.md#get-adminuserspending)).

- **Seguridad.** Público, con validación de origen y como máximo 10 registros por IP en una hora (`APP_REGISTRATION_MAX_PER_IP_PER_HOUR`).
- **Entrada.** JSON con `nombreCompleto`, `correo`, `contrasena` y `confirmacionContrasena`. El correo debe terminar exactamente en `@unmsm.edu.pe` (`APP_REGISTRATION_EMAIL_DOMAIN`), sin subdominios; se compara sin distinguir mayúsculas ni espacios. Cualquier otro campo, como `rol`, se ignora: siempre se crea un `ESTUDIANTE` sin área.
- **Salida.** `201` con el mensaje para mostrar. No se emite sesión ni se envía correo.
- **Errores.** `400` por campos vacíos, dominio distinto, contraseña fuera de la política o confirmación distinta (con `errores[].campo`); `409 CORREO_REGISTRADO` si el correo ya existe en cualquier estado, sin revelar a quién pertenece; `429` por exceso de registros.

**Petición de ejemplo**

```http
POST /api/v1/auth/register
Content-Type: application/json
Origin: https://app.gestal.example

{
  "nombreCompleto": "Lucía Demo",
  "correo": "lucia.demo@unmsm.edu.pe",
  "contrasena": "<contrasena-elegida>",
  "confirmacionContrasena": "<contrasena-elegida>"
}
```

**Respuesta de ejemplo**

```http
HTTP/1.1 201 Created
Content-Type: application/json

{
  "mensaje": "Registro enviado. Podrás ingresar cuando un administrador apruebe tu cuenta"
}
```

## `POST /auth/login`

Inicia sesión con una cuenta activa, creada por un administrador o aprobada tras el auto-registro.

- **Seguridad.** Público, con límite de intentos. Se valida el origen en el flujo del navegador.
- **Entrada.** JSON con `correo` y `contrasena`, ambos obligatorios.
- **Salida.** JWT de acceso, usuario, indicador de cambio de contraseña y cookie de renovación.
- **Errores.** `400` si faltan campos, `401` por credenciales inválidas o cuenta desactivada y `429` por exceso de intentos. Si la contraseña es correcta pero la cuenta está pendiente de aprobación o fue rechazada, `403 CUENTA_PENDIENTE` o `403 CUENTA_RECHAZADA`, sin emitir sesión; con contraseña incorrecta esas cuentas reciben el mismo `401` que cualquier otra.

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
- **Errores.** `400 VALIDACION` por datos o contraseña nueva inválidos, o si la contraseña actual no coincide (en ese caso `errores[0].campo = "contrasenaActual"` y la sesión sigue vigente). `401` solo si falta o venció el JWT.

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
