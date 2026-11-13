# Integración del frontend con el backend

Guía para consumir la API de Gestal desde `frontend-uni`. El contrato completo y vigente es [`docs/api/openapi.yaml`](api/openapi.yaml), con 64 rutas generadas desde el código. Las páginas de [`docs/api/`](api/) explican las reglas de cada módulo.

## Entorno local

1. Levante el Compose de [infra-uni](https://github.com/taller-web-sigesu/infra-uni): `docker compose up -d --build`.
2. El frontend corre en `http://localhost:3000` y Vite reenvía `/api` a `http://backend:8080`. Desde el navegador, use rutas relativas (`/api/v1/...`): la cookie de sesión queda en el mismo origen y no hace falta CORS.
3. La documentación interactiva está en `http://localhost:8080/swagger-ui.html` y el JSON del contrato en `http://localhost:8080/v3/api-docs`.

Si ejecuta `npm run dev` fuera de Docker, el nombre `backend` no se resuelve: el proxy de `vite.config.js` debe apuntar a `http://localhost:8080`.

El backend acepta por defecto los orígenes `http://localhost:3000` y `http://127.0.0.1:3000` (`APP_ALLOWED_ORIGINS`, separados por comas). Cualquier otro origen recibe `403` en las operaciones con cookie y en el preflight CORS.

## Cuentas de demostración

Todas usan la contraseña `Demo2026!`, están activas y no exigen cambio de contraseña.

| Correo | Rol | Área |
|---|---|---|
| `admin.demo@universidad.example` | ADMINISTRADOR | — |
| `supervisor.ti.demo@universidad.example` | SUPERVISOR | TI (1) |
| `supervisor.mant.demo@universidad.example` | SUPERVISOR | Mantenimiento (2) |
| `supervisor.bib.demo@universidad.example` | SUPERVISOR | Biblioteca (4) |
| `tecnico.ti.demo@universidad.example` | TECNICO | TI (1) |
| `tecnica.ti.demo@universidad.example` | TECNICO | TI (1) |
| `tecnico.mant.demo@universidad.example` | TECNICO | Mantenimiento (2) |
| `ana.demo@universidad.example` | ESTUDIANTE | — |
| `pedro.demo@universidad.example` | DOCENTE | — |

Para probar el primer ingreso, cree un usuario desde `POST /admin/users`: nace con `debeCambiarContrasena: true`. Para el auto-registro (HU-27), use un correo `@unmsm.edu.pe` y apruebe la cuenta con el administrador.

## Sesión

| Paso | Petición | Qué hace el frontend |
|---|---|---|
| Ingreso | `POST /api/v1/auth/login` con `{correo, contrasena}` | Guarda `tokenAcceso` **solo en memoria** junto con `usuario` y `debeCambiarContrasena`. El navegador guarda la cookie `refresh_token` (`HttpOnly`, `SameSite=Lax`, `Path=/api/v1/auth`). |
| Peticiones | `Authorization: Bearer <tokenAcceso>` | El token dura 15 minutos (`expiraEnSegundos: 900`). |
| Renovación | `POST /api/v1/auth/refresh` sin cuerpo | Devuelve la misma forma que el login y rota la cookie. Úsela al recargar la página para restaurar la sesión y ante un `401` en una operación de negocio, reintentando una sola vez. Comparta una única renovación entre peticiones simultáneas: reutilizar una cookie ya rotada revoca la sesión. |
| Cambio de contraseña | `PATCH /api/v1/auth/password` | Reemplaza el token y la cookie con la respuesta. |
| Salida | `POST /api/v1/auth/logout` | `204`. Revoca el token y la cookie de inmediato. |

- Con `fetch` en el mismo origen la cookie viaja sola (`credentials: 'same-origin'`, el valor por defecto). Si el frontend llama a otro origen, use `credentials: 'include'`.
- El backend comprueba el encabezado `Origin` en login, registro, refresh y logout por cookie (un origen ajeno recibe `403 ORIGEN_NO_PERMITIDO`). El navegador lo envía solo; no lo fije a mano.
- Con `debeCambiarContrasena: true`, cualquier operación de negocio responde `403 CAMBIO_CONTRASENA_REQUERIDO`: lleve al usuario a la pantalla de cambio obligatorio.
- `403 CUENTA_PENDIENTE` y `403 CUENTA_RECHAZADA` solo aparecen en el login con la contraseña correcta. Con la contraseña incorrecta siempre es `401`.
- Demasiados intentos de ingreso devuelven `429`.

## Formato de errores

Todos los errores son `application/problem+json`:

```json
{
  "title": "Solicitud inválida",
  "status": 400,
  "detail": "Los datos enviados no son válidos.",
  "instance": "/api/v1/auth/login",
  "codigo": "DATOS_INVALIDOS",
  "correlationId": "b8b6836d-…",
  "errores": [{ "campo": "correo", "mensaje": "correo es obligatorio." }]
}
```

- Decida por `status` y `codigo`; muestre `detail` y asocie `errores[].campo` al campo del formulario. `errores` solo aparece cuando hay errores de campo.
- `correlationId` también llega en el encabezado `X-Correlation-ID`. Muéstrelo en los errores inesperados para que se pueda rastrear en los logs.
- Los códigos de cada operación están en su página de `docs/api/`.

## Convenciones de datos

- Los identificadores llegan como cadenas (`"id": "12"`). No los convierta a número.
- Los listados aceptan `page` (desde 0) y `size` (20 por defecto, máximo 100) y responden `{items, page, size, totalElements}`.
- Los instantes (`creadoEn`, vencimientos de SLA, auditoría) están en UTC (ISO 8601). Las franjas de reserva usan `fecha` (`YYYY-MM-DD`), `horaInicio` y `horaFin` (`HH:mm`) en hora de Lima.
- Las altas devuelven `201` con `Location`, y las operaciones sin cuerpo devuelven `204`.
- Las descargas (`GET /dashboard/reports` en CSV y el contenido de evidencias) llegan con `Content-Disposition`. El contenido de una evidencia puede responder `200` con el archivo o `302` hacia una URL firmada; `fetch` sigue la redirección solo.
- Las evidencias se suben con `multipart/form-data` en el campo `archivo` (JPG, PNG o PDF de hasta 5 MB, máximo 5 por solicitud).

## Comprobación rápida

Con el Compose levantado, estas peticiones por el proxy del frontend deben responder `200`, `200`, `200`, `204` y `401` en ese orden:

```bash
curl -s -c c.txt -H 'Origin: http://localhost:3000' -H 'Content-Type: application/json' -d '{"correo":"admin.demo@universidad.example","contrasena":"Demo2026!"}' -o login.json -w '%{http_code}\n' http://localhost:3000/api/v1/auth/login
TOKEN=$(sed -n 's/.*"tokenAcceso":"\([^"]*\)".*/\1/p' login.json)
curl -s -o /dev/null -w '%{http_code}\n' -H "Authorization: Bearer $TOKEN" http://localhost:3000/api/v1/admin/users
curl -s -b c.txt -c c.txt -o /dev/null -w '%{http_code}\n' -H 'Origin: http://localhost:3000' -X POST http://localhost:3000/api/v1/auth/refresh
curl -s -b c.txt -c c.txt -o /dev/null -w '%{http_code}\n' -H 'Origin: http://localhost:3000' -X POST http://localhost:3000/api/v1/auth/logout
curl -s -o /dev/null -w '%{http_code}\n' -H "Authorization: Bearer $TOKEN" http://localhost:3000/api/v1/admin/users
```
