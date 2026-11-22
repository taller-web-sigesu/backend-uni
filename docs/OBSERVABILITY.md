# Contrato de telemetría de backend-uni

## Recolección

Prometheus usa `GET /actuator/prometheus` con Basic Auth sobre HTTPS o loopback local. El usuario fijo es `prometheus`. El backend recibe el hash BCrypt mediante `APP_TELEMETRY_PASSWORD_HASH` y el recolector recibe la contraseña original desde su almacén de secretos. Con el hash vacío, todas las peticiones de recolección se rechazan. La misma cuenta permite consultar `GET /actuator/info`, `GET /actuator/metrics` y `GET /actuator/metrics/{nombre}`. Las sesiones JWT de la aplicación carecen de acceso a estas rutas, incluida la sesión de administrador.

`GET /actuator/health` conserva la respuesta pública mínima con `status` y los nombres de grupos disponibles para Compose y staging. La respuesta omite detalles y componentes internos. `GET /api/v1/status` conserva su contrato público.

Actuator admite únicamente las lecturas autorizadas por su cadena. Spring Security aplica CSRF a los métodos de escritura y usa una cookie limitada a `/actuator` para mantener la cadena sin sesión del servidor. El scrape mediante `GET` funciona con la credencial de recolección.

## Métricas

| Serie Prometheus | Unidad | Etiquetas | Semántica |
| --- | --- | --- | --- |
| `http_server_requests_seconds_count` | peticiones | `application`, `method`, `uri`, `status`, `outcome`, `error`, `exception` | Contador HTTP. Para la tasa de 5xx, filtrar por `status=~"5.."` y dividir la tasa resultante entre la tasa total sobre el mismo conjunto de rutas. |
| `http_server_requests_seconds_sum`, `http_server_requests_seconds_bucket` | segundos acumulados en `_sum`, peticiones por umbral en `_bucket` | Las de HTTP y `le` en los buckets | Para p95 agregado, usar `histogram_quantile(0.95, sum by (le) (rate(http_server_requests_seconds_bucket[5m])))`. Incluye un umbral de 2 s. |
| `jvm_memory_used_bytes`, `jvm_memory_max_bytes` | bytes | `area`, `id` | Memoria por área y pool JVM. |
| `jvm_threads_live_threads` | hilos | Sin etiquetas propias | Hilos activos de la JVM. |
| `process_cpu_usage`, `system_cpu_usage` | fracción de 0 a 1 | Sin etiquetas propias | Uso reciente de CPU. |
| `hikaricp_connections_active`, `hikaricp_connections_idle`, `hikaricp_connections_pending`, `hikaricp_connections_max` | conexiones, solicitudes en espera para `_pending` | `pool` | Saturación del pool: activas divididas por el máximo del mismo pool. |
| `gestal_business_operations_total` | operaciones | `operation=request_created\|reservation_created` | Altas confirmadas por la transacción de MySQL. |
| `gestal_auth_login_rejections_total` | intentos | `reason=invalid_credentials\|rate_limited` | Credenciales inválidas o intentos rechazados por el limitador. Ambos motivos se pueden agregar para una alerta. |

Para medir solo tráfico de la API, aplicar el mismo filtro de rutas al numerador, denominador e histograma. `uri` contiene plantillas de ruta y valores acotados para rutas sin resolver. Los contadores son por instancia y se reinician con el proceso.

## Trazas y logs

El backend genera spans HTTP con Micrometer Tracing y OpenTelemetry y acepta contexto W3C `traceparent`. Configurar `APP_OTLP_ENABLED=true` para enviar trazas por HTTP/protobuf a `APP_OTLP_TRACES_ENDPOINT` (URL completa, incluida `/v1/traces`). Si el collector requiere cabeceras, usar la propiedad de Spring Boot `management.opentelemetry.tracing.export.otlp.headers` desde la configuración secreta del entorno. `APP_TRACING_SAMPLE_RATE` controla el muestreo de trazas iniciadas en el backend y por defecto vale `0.1`. Para la prueba local usar `1.0`.

Los logs JSON salen por consola con `timestamp`, `level`, `logger` y `message`. La línea de finalización de cada petición a la API contiene además `correlationId`, `traceId`, `spanId` y el código HTTP. Buscar primero el valor de `X-Correlation-ID` de la respuesta en logs y luego usar `traceId` para abrir la traza y `spanId` para identificar el span. Se omiten URLs de alta cardinalidad en spans, cuerpos, tokens, correos y cabeceras de autorización.

## Señal existente de despliegue

- Staging se despliega automáticamente desde `main` en Render. Los eventos de deploy del servicio de Render son la fuente para identificar la revisión desplegada. [Arquitectura del backend](../ARCHITECTURE.md).
- [staging-smoke](../.github/workflows/staging-smoke.yml) corre en horario programado o manual y mide disponibilidad y flujos de la API.
- [deploy-production](../.github/workflows/deploy-production.yml) llama el hook de Render desde GitHub Actions y verifica disponibilidad y smoke. Para asociar una revisión con un despliegue efectivo, usar el evento de Render.

El dashboard, las reglas, el canal de alertas y el runbook corresponden a B.
