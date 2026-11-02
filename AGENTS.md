# AGENTS.md — Backend

## Política obligatoria de ramas
- Nunca edites ni confirmes cambios directamente en `main`.
- Antes de modificar código, verifica la rama actual y crea una rama por tarea desde `main` (`feature/...`, `fix/...` o `docs/...`).
- Integra cambios a `main` únicamente mediante una PR revisada por otro compañero.

## Dev environment tips
- Stack: Java + Spring Boot + Maven + MySQL.
- Read `ARCHITECTURE.md` when available.
- Find user stories in `../infra-uni/docs/requirements/user-stories/`.
- Follow the existing layered architecture and package conventions.
- Keep controllers, business logic, and persistence responsibilities separate.
- Use environment variables for configuration and credentials.
- Use Docker Compose from `infra-uni` for the shared local environment.
- Prefer the Maven Wrapper when available.

## Testing instructions
- Run `./mvnw test` on Linux/macOS or `mvnw.cmd test` on Windows when the wrapper exists.
- Otherwise use `mvn test`.
- Add or update unit and integration tests for changed behavior.
- Verify validation, authorization, and error handling where applicable.
- Check database migrations when modifying persistence.
- Do not claim tests passed unless they were executed.

## PR instructions
- Title format: `[backend] Brief description`.
- Keep changes focused on the assigned user story.
- Follow the agreed API contracts and HTTP conventions.
- Do not expose credentials or sensitive information.
- Document API or database changes.
- Include a summary of changes and tests performed.
