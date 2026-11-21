# Observabilidad local de Gestal

El panel de Grafana usa el [contrato de telemetría de A](https://github.com/taller-web-sigesu/backend-uni/blob/fc6bbbea54eab85b4a52fd76e389ec9b614e2edb/docs/OBSERVABILITY.md). Los umbrales de disponibilidad, 5xx, p95 y pool siguen el [ADR-0003](https://github.com/taller-web-sigesu/infra-uni/blob/main/docs/adr/ADR-0003-observabilidad-retencion.md). Las ventanas de evaluación, el umbral de login y el evento de despliegue son decisiones operativas de esta entrega.

## Iniciar

1. En un entorno aislado con Docker Compose, iniciar solo `mysql` y `backend` de `infra-uni` con el código de A y datos ficticios. Usar un nombre de proyecto propio si se comparte la máquina. El recolector se conecta a la red `<proyecto>_app_net` y usa el alias `backend:8080`.
2. En el servicio `backend` de un override local de Compose, pasar `APP_TELEMETRY_PASSWORD_HASH` desde el entorno, `APP_OTLP_ENABLED: "true"`, `APP_OTLP_TRACES_ENDPOINT: http://tempo:4318/v1/traces` y `APP_TRACING_SAMPLE_RATE: "1.0"`. Cargar el hash BCrypt desde un secreto local. Crear, fuera del repositorio, un archivo que solo contenga la contraseña original de la cuenta técnica local `prometheus`. La contraseña y su hash deben corresponder según el contrato de A.
3. Definir `GESTAL_SCRAPE_PASSWORD_FILE` con la ruta del archivo legible por el contenedor de Prometheus y `GESTAL_OBSERVABILITY_NETWORK` con la red del paso 1. Desde `observability/`, ejecutar:

   ```sh
   docker compose config -q
   docker compose up -d
   docker compose ps
   ```

4. Abrir [Grafana](http://127.0.0.1:3001/d/gestal-backend) y [Targets de Prometheus](http://127.0.0.1:9090/targets). Confirmar `up{job="gestal-backend"} == 1`, `probe_success{job="gestal-health"} == 1` y revisar que aparezcan las series JVM y Hikari. Generar tráfico solo en la API local. Para ver altas de negocio, efectuar una solicitud o reserva con una cuenta demo y confirmar el 201. La interfaz local de Grafana utiliza el rol Viewer y escucha en loopback. Las alertas se consultan en [Prometheus](http://127.0.0.1:9090/alerts). El canal del equipo, las credenciales del monitor y la gestión de acceso en staging dependen de P2 y operaciones.

El monitor de deploy hace una consulta de solo lectura al repositorio público de GitHub. Expone el resultado de la última ejecución completada de la rama `production` de `deploy-production.yml`. `failure`, `timed_out`, `startup_failure` y `action_required` se cuentan como fallo. La cancelación y las demás conclusiones sin resultado definitivo dejan la serie de fallo sin dato. Si falla la lectura de GitHub el scrape queda DOWN. El identificador de ejecución se expone como valor, con nombre `gestal_deployment_workflow_run_id`. La prueba de la revisión efectiva usa el evento de Render.

## Confirmar, diagnosticar y recuperar

| Alerta | Confirmar | Revisar | Recuperar y comprobar |
| --- | --- | --- | --- |
| `GestalHealthUnavailable` | Después de 1 min, consultar `probe_success` y `up{job="gestal-health"}`. Desde la red de la sonda, comprobar que `GET /actuator/health` da HTTP 200 con `status=UP`. | Si falla el scrape, revisar sonda, red y DNS. Si falla el endpoint, revisar arranque, dependencias y conectividad de MySQL. | Restaurar sonda, red o dependencia según el diagnóstico. Si una revisión impide arrancar, coordinar la recuperación con la última revisión estable. Confirmar `probe_success=1` y resolución de la alerta. |
| `GestalHttp5xxHigh` | Confirmar más de 1 % durante 5 min en `gestal:http_5xx_ratio:rate5m` y validar que hubo tráfico en `gestal:http_requests_per_second:rate5m`. | Separar 5xx por plantilla `uri` y código `status`. Buscar el error y su `traceId` en logs JSON y comprobar si coincide con un cambio o fallo de base. | Corregir la dependencia o revertir el cambio con operaciones. Confirmar peticiones válidas y una proporción ≤ 1 % con tráfico representativo. |
| `GestalHttpP95High` | Confirmar p95 > 2 s durante 5 min en `gestal:http_p95_seconds:rate5m`. Comprobar volumen y buckets del histograma. | Acotar las rutas lentas y revisar spans, esperas por conexión, CPU y consultas SQL. | Mitigar la ruta o dependencia confirmada. Repetir la carga local representativa y verificar p95 ≤ 2 s y resolución. |
| `GestalPoolSaturated` | Localizar `instance` y `pool` con activas/máximo > 0,8 durante 5 min. Consultar `hikaricp_connections_pending`. | Revisar transacciones largas, bloqueos MySQL, latencia, errores de adquisición y el límite total de conexiones de la base. | Reducir carga o liberar bloqueos con el responsable de MySQL. Ajustar capacidad solo con evidencia de espacio en la base. Confirmar utilización ≤ 80 % y desaparición de esperas sostenidas. |
| `GestalDeploymentFailed` | Confirmar valor 1 durante 1 min y scrape UP. Tomar el valor de `gestal_deployment_workflow_run_id` y abrir la ejecución en [Actions](https://github.com/taller-web-sigesu/backend-uni/actions/workflows/deploy-production.yml). | Revisar qué paso falló (hook, espera o smoke), su SHA y el evento de Render. Verificar la revisión que sirve tráfico. | Corregir el paso con el responsable de producción. Según el estado de Render, restaurar la última revisión estable o reejecutar el flujo aprobado. Confirmar una nueva ejecución exitosa y el smoke contra la revisión esperada. |
| `GestalLoginRejectionsHigh` | Confirmar que la suma de `increase(gestal_auth_login_rejections_total[5m])` para ambos motivos llega a 20 durante 2 min. | Separar `invalid_credentials` y `rate_limited`. Revisar cambios de cliente, salud y registros de acceso autorizados sin incluir credenciales ni datos de cuentas. | Corregir reintentos del cliente o escalar el abuso confirmado. Mantener los controles de autenticación y confirmar una ventana normal con una cuenta demo. |

Para correlacionar una petición, enviar `GET /api/v1/status` con un `X-Correlation-ID` ficticio. Conservar código HTTP y cabecera de respuesta, buscar la línea JSON en `docker compose logs backend` y consultar su `traceId` en Grafana → Explore → Gestal Tempo. Comparar `spanId` con el span de esa petición. En la evidencia usar únicamente los campos de correlación, la ruta de prueba, la hora y el estado.

## Retención

| Señal | ADR-0003 | Configuración local | Confirmación |
| --- | --- | --- | --- |
| Métricas | 15 días | Prometheus con `--storage.tsdb.retention.time=15d` y volumen persistente. | Consultar `/api/v1/status/flags` y la configuración efectiva del contenedor. |
| Trazas | 7 días | Tempo con `block_retention: 168h` y volumen persistente. | Consultar la configuración efectiva de Tempo dentro de la red y revisar que funciona el compactador. |
| Logs técnicos | 30 días | Logs JSON de la consola de A, consultados en el entorno local. | La duración depende del driver y de la vida del contenedor. Pendiente demostrar 30 días en el proveedor elegido. |

La configuración expresa políticas de métricas y trazas. La purga por antigüedad y la retención de staging requieren evidencia del proveedor y de datos de la edad correspondiente. La tabla excluye `auditoria_eventos`.

## Repetir la prueba sintética

Desde `observability/`, ejecutar `promtool check rules prometheus/gestal.rules.yml` y `promtool test rules prometheus/gestal.test.yml`. La fixture prueba las seis alertas antes de disparar, con la condición sostenida y tras recuperarse. Incluye un escenario con sonda ausente, tráfico cero y fallo de lectura del workflow.
