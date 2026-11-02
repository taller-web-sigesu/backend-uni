# Backend — Universidad Actúa

API base en Java 21 + Spring Boot 4.1.2. Incluye Spring Web, Spring Data JPA,
MySQL Driver y Actuator; todavía no contiene lógica de negocio ni tablas.

- `GET /api/v1/status`: confirma que la API responde.
- `/actuator/health`: estado del servicio y su conexión a MySQL.
- La conexión se configura con `SPRING_DATASOURCE_URL`,
  `SPRING_DATASOURCE_USERNAME` y `SPRING_DATASOURCE_PASSWORD`.
- Docker: inicia el entorno desde `infra-uni/iniciar.bat`; para recompilar
  cambios Java usa `mvn compile` en **Exec** del contenedor `backend`.

Maven y Java se ejecutan dentro de Docker; no hace falta instalarlos en el host.

## Trabajo en equipo

**Obligatorio:** crea una rama por tarea (`feature/...`, `fix/...` o `docs/...`)
desde `main`. No cambies ni confirmes trabajo directamente en `main`; integra
mediante una PR revisada por otro compañero.
