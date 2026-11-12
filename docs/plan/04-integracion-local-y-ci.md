# Fase 04 · Integración local y CI

**Objetivo.** Dejar el backend reproducible desde Docker local y comprobar en GitHub Actions el contrato, la compilación, las migraciones, las pruebas y el arranque de la imagen.

**Entrada.** Contratos y fases funcionales cerrados. Consultar la [guía Docker](https://github.com/taller-web-sigesu/infra-uni/blob/main/docs/DOCKER.md), el [ADR de CI](https://github.com/taller-web-sigesu/infra-uni/blob/main/docs/adr/ADR-0005-ci-cd-calidad.md) y el [ADR de observabilidad](https://github.com/taller-web-sigesu/infra-uni/blob/main/docs/adr/ADR-0003-observabilidad-retencion.md) para las decisiones aplicables a local y CI.

## Tareas

- [ ] **Cerrar la verificación Maven.** Ejecutar unitarias con Surefire e integración con Failsafe dentro de `mvn -B verify`. Configurar Testcontainers para la versión de MySQL usada en Compose. Mantener bases desechables y datos ficticios para probar migraciones, concurrencia y permisos. Documentar ejecución equivalente mediante contenedor Maven si el host no tiene Java y Maven.
- [ ] **Configurar el workflow del backend.** Ejecutar en PR y en cambios de la rama principal con Java 21 y Docker. Validar OpenAPI, ejecutar `mvn -B verify`, análisis estático y escaneo de dependencias. Conservar reportes y salida útil para diagnóstico. Definir el check de verificación como requisito para integrar, junto con la revisión de otro integrante.
- [ ] **Verificar la imagen.** Construir la imagen del backend y arrancarla en CI contra MySQL temporal. Esperar salud con un tiempo máximo y recoger logs si el arranque falla. Comprobar salud, migraciones, login de cuenta ficticia y acceso a una ruta protegida. Verificar que la misma ruta rechaza una petición sin credencial. Limpiar contenedores temporales al terminar.
- [ ] **Reproducir el recorrido local.** Documentar las variables requeridas, arranque desde Compose, preparación de una base nueva, aprovisionamiento ficticio y comandos de verificación. Recorrer autenticación y cierre de sesión, solicitud hasta cierre y reserva hasta cancelación o decisión. Usar los ejemplos de Postman contra el backend real para verificar el humo.
- [ ] **Incorporar diagnóstico operativo básico.** Usar correlación, Actuator y Micrometer para salud, latencia y errores. Verificar que logs y reportes excluyen tokens, contraseñas y cuerpos sensibles. Mantener las rutas operativas con el alcance de exposición acordado.

## Evidencia y cierre

| Verificación | Evidencia esperada |
| --- | --- |
| Maven | `mvn -B verify` exitoso con reportes separados de unitarias e integración. |
| Contrato | OpenAPI válido y casos de integración que verifican el formato HTTP y la autorización. |
| Base vacía y reinicio | Compose y CI arrancan con Flyway, y el segundo arranque conserva el esquema sin volver a aplicar semillas. |
| Imagen | Build exitoso y salida de los controles de salud, login y ruta protegida. |
| Recorridos locales | Comandos y resultados reproducibles con cuentas ficticias y trazabilidad a las HU. |
| Diagnóstico | Correlación rastreable entre respuesta y log, sin credenciales en artefactos. |

La entrega se considera cerrada cuando otro integrante puede clonar los repositorios, arrancar el backend con Compose y reproducir los recorridos y la verificación Maven. La salida de CI y la revisión de PR respaldan el cierre.
