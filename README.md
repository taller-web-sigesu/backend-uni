# Backend — Universidad Actúa (Gestal)

API REST en Java 21 + Spring Boot 4.1.2 sobre MySQL 8.4. Implementa HU-01 a HU-29: sesión, auto-registro con aprobación, restablecimiento de contraseña, solicitudes con evidencias, atención, SLA, auditoría, encuesta, dashboard, usuarios, catálogos y reservas de ambientes.

- Contrato: [`docs/api/openapi.yaml`](docs/api/openapi.yaml) (generado por la aplicación en `/v3/api-docs`; Swagger UI en `/swagger-ui.html`).
- Integración con el frontend (sesión, errores, cuentas de demostración): [`docs/INTEGRACION-FRONTEND.md`](docs/INTEGRACION-FRONTEND.md).
- Seguridad y permisos: [`docs/SECURITY.md`](docs/SECURITY.md). Base de datos: [`docs/DATABASE.md`](docs/DATABASE.md).
- Diseño: [API por negocio](docs/API-DISENIO.md), [diseño del backend](docs/BACKEND-DISENIO.md) y [planes por fase](docs/plan/).

## Despliegues

Hay **dos despliegues separados**:

| Entorno | Backend | Swagger | Rama | Base de datos |
| --- | --- | --- | --- | --- |
| Staging | https://uniservice-backend-staging.onrender.com | https://uniservice-backend-staging.onrender.com/swagger-ui.html | `main` | MySQL en Railway |
| Producción | https://uniservice-backend-prod.onrender.com | https://uniservice-backend-prod.onrender.com/swagger-ui.html | `production` | MySQL en Railway (instancia distinta) |

- **Staging:** Render despliega automáticamente al actualizar `main` y carga las cuentas demo (`APP_FLYWAY_LOCATIONS=classpath:db/migration,classpath:db/demo`).
- **Producción:** Render tiene `Auto-Deploy: Off`. El workflow `deploy-production.yml` exige aprobación del environment `production`, llama al Deploy Hook, espera `health = UP` y ejecuta el smoke. Solo aplica migraciones, sin datos demo.
- **Frontends:** staging usa https://frontend-uni-tdk7.vercel.app y producción https://frontend-uni-wx9m.vercel.app; cada uno hace proxy `/api` a su backend.

### Usuarios de prueba (staging)

Todas las cuentas usan la contraseña **`Demo2026!`**:

| Correo | Rol | Área |
| --- | --- | --- |
| `admin.demo@universidad.example` | Administrador | — |
| `supervisor.ti.demo@universidad.example` | Supervisor | TI (1) |
| `supervisor.mant.demo@universidad.example` | Supervisor | Mantenimiento (2) |
| `supervisor.bib.demo@universidad.example` | Supervisor | Biblioteca (4) |
| `tecnico.ti.demo@universidad.example` | Técnico | TI (1) |
| `tecnica.ti.demo@universidad.example` | Técnica | TI (1) |
| `tecnico.mant.demo@universidad.example` | Técnico | Mantenimiento (2) |
| `ana.demo@universidad.example` | Estudiante | — |
| `pedro.demo@universidad.example` | Docente | — |

Producción no carga cuentas demo. Su administrador inicial es `admin@uniservice.app`; la contraseña se entrega por el canal privado y el sistema obliga a cambiarla en el primer ingreso.

El informe completo de Fase III está en [infra-uni/docs/fase-3/INFORME-FASE-III.md](https://github.com/taller-web-sigesu/infra-uni/blob/main/docs/fase-3/INFORME-FASE-III.md).

## Variables de entorno

| Variable | Uso | Valor local por defecto |
| --- | --- | --- |
| `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` | Conexión MySQL | Base `uni_services` del Compose |
| `APP_JWT_SECRET` | Firma del JWT (mínimo 32 bytes). **Obligatorio fuera de local.** | Secreto de desarrollo |
| `APP_COOKIE_SECURE` | `Secure` en la cookie de refresh | `true` (los navegadores lo aceptan en `http://localhost`) |
| `APP_ALLOWED_ORIGINS` | Orígenes CORS y de cookie, separados por coma | `http://localhost:3000,http://127.0.0.1:3000` |
| `APP_FLYWAY_LOCATIONS` | Añadir `classpath:db/demo` carga cuentas ficticias | Solo migraciones |
| `APP_BOOTSTRAP_ADMIN_EMAIL`, `APP_BOOTSTRAP_ADMIN_PASSWORD` | Primer administrador si no existe ninguno | Vacío |
| `APP_LOGIN_MAX_ATTEMPTS`, `APP_LOGIN_WINDOW` | Límite de intentos de login | `5`, `15m` |
| `APP_REGISTRATION_EMAIL_DOMAIN`, `APP_REGISTRATION_MAX_PER_IP_PER_HOUR` | Dominio exacto del auto-registro y límite de registros por IP | `unmsm.edu.pe`, `10` |
| `APP_STORAGE_PROVIDER` | Almacén de evidencias: `local` o `supabase` | `local` |
| `APP_STORAGE_LOCAL_DIR` | Carpeta de evidencias con `local` (monte un volumen) | `./data/evidencias` (`/data/evidencias` en la imagen) |
| `APP_STORAGE_SUPABASE_URL`, `APP_STORAGE_SUPABASE_SERVICE_KEY`, `APP_STORAGE_SUPABASE_BUCKET` | Supabase Storage (Fase 5). La clave de servicio es secreta y nunca llega al frontend. | Vacío, vacío, `evidencias-solicitudes` |

## Arranque local con Docker

1. Inicia el entorno desde `infra-uni` (`iniciar.bat` o `docker compose up -d --build`). Flyway migra la base vacía al arrancar.
2. El Compose local ya define `APP_FLYWAY_LOCATIONS=classpath:db/migration,classpath:db/demo` y carga cuentas ficticias (contraseña `Demo2026!`): `admin.demo`, `supervisor.ti.demo`, `tecnico.ti.demo`, `ana.demo` (estudiante) y `pedro.demo` (docente), todas `@universidad.example`. La lista completa está en la [guía de integración](docs/INTEGRACION-FRONTEND.md#cuentas-de-demostración).
3. Sin datos ficticios, aprovisiona el primer administrador con `APP_BOOTSTRAP_ADMIN_EMAIL` y `APP_BOOTSTRAP_ADMIN_PASSWORD`; deberá cambiar la contraseña en su primer ingreso.
4. Para recompilar cambios Java usa `mvn compile` en **Exec** del contenedor `backend`.

## Verificación

Maven y Java pueden ejecutarse dentro de Docker; las pruebas de integración necesitan acceso al daemon de Docker (Testcontainers con `mysql:8.4.13`).

```bash
mvn -B verify
```

Sin Java ni Maven en el host (Docker Desktop):

```bash
docker run --rm -v "$PWD":/workspace -w /workspace -v /var/run/docker.sock:/var/run/docker.sock -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal maven:3.9.16-eclipse-temurin-21 mvn -B verify
```

Imagen de ejecución y prueba de humo (MySQL temporal, salud, login ficticio, ruta protegida, auto-registro pendiente y reinicio):

```bash
bash scripts/smoke-image.sh
```

- Unitarias (`*Test`) con Surefire; integración (`*IT`) con Failsafe contra MySQL real.
- `OpenApiContractIT` comprueba que se publican las 64 rutas del diseño y exporta `target/openapi.yaml`. Si cambias el contrato, cópialo a `docs/api/openapi.yaml`; CI falla si difieren.
- CI (`.github/workflows/backend-ci.yml`): lint OpenAPI, `mvn verify`, imagen + humo, CodeQL y revisión de dependencias en PR.

## Trabajo en equipo

**Obligatorio:** crea una rama por tarea (`feature/...`, `fix/...` o `docs/...`) desde `main`. No cambies ni confirmes trabajo directamente en `main`; integra mediante una PR revisada por otro compañero.
