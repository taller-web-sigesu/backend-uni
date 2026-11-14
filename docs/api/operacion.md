# Operación y salud

> Rutas existentes y controles de operación propuestos. Operación transversal.

[Convenciones de API](../API-DISENIO.md#convenciones-del-contrato) · [Diseño del backend](../BACKEND-DISENIO.md)

Ambas rutas están implementadas. `/actuator/health` es público y devuelve solo el estado agregado (`UP`); el detalle de componentes queda restringido. Swagger y `/v3/api-docs` están publicados para la evaluación y deben restringirse si el sistema pasa a operación real.

## `GET /status`

Comprueba que el controlador de la API responde.

- **Seguridad.** Ruta pública en la base actual.
- **Entrada.** Sin parámetros ni cuerpo.
- **Salida.** Indicador fijo de servicio. La salud de MySQL se consulta por separado.
- **Errores.** El controlador actual no define errores propios de negocio.

**Petición de ejemplo**

```http
GET /api/v1/status
```

**Respuesta de ejemplo**

```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "service": "backend",
  "status": "UP"
}
```

## `GET /actuator/health`

Consulta la salud que reporta Spring Boot Actuator.

- **Seguridad.** Expuesto por la configuración actual. En el diseño objetivo se restringe a monitoreo interno o autorizado.
- **Entrada.** Sin parámetros ni cuerpo.
- **Salida.** Estado agregado de salud, con detalles ocultos por configuración.
- **Errores.** Puede devolver `503` si Actuator reporta un estado no saludable.

**Petición de ejemplo**

```http
GET /actuator/health
```

**Respuesta de ejemplo**

```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "status": "UP"
}
```
