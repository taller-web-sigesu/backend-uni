# Plan de implementación de la API de Gestal en Postman

> Ejecutar las tareas en orden y marcar cada casilla después de verificar su resultado.

**Objetivo** — Simular las 57 operaciones documentadas de Gestal y comprobar en Postman el código HTTP esperado de cada escenario.

**Enfoque** — Crear una colección HTTP con ejemplos guardados y un entorno importable. Un Mock Server de Postman devolverá el ejemplo seleccionado por cada petición, y el script posterior a la respuesta comprobará el código recibido. Los casos se ejecutarán individualmente con **Send** o en conjunto con **Collection Runner**.

**Herramientas** — Postman Collection Format v2.1, entorno Postman en JSON, Mock Server basado en colección y JavaScript de Postman Sandbox.

**Contrato de referencia** — [Diseño de la API de Gestal](../API-DISENIO.md) y los contratos por módulo enlazados en la tabla de cobertura. El diseño de la API es una propuesta del equipo.

## Entregables previstos

| Archivo | Contenido |
| --- | --- |
| `gestal.postman_collection.json` | Peticiones por módulo, ejemplos de respuesta y tests por escenario. |
| `gestal-mock.postman_environment.json` | URL del mock, variables con datos ficticios y un campo vacío para la clave del mock privado. |

Guardar ambos JSON en `backend-uni/docs/postman-disenio/` durante la implementación. El mock reproducirá respuestas predefinidas. La comprobación de permisos reales, persistencia, rotación de tokens y cambios de estado entre peticiones corresponde a pruebas contra el backend.

## Cobertura

| Carpeta en Postman | Fuente | Operaciones |
| --- | --- | ---: |
| Autenticación | [autenticacion.md](../api/autenticacion.md) | 4 |
| Solicitudes | [solicitudes.md](../api/solicitudes.md) | 6 |
| Atención | [atencion.md](../api/atencion.md) | 4 |
| Dashboard y reportes | [dashboard.md](../api/dashboard.md) | 2 |
| Usuarios y roles | [usuarios.md](../api/usuarios.md) | 5 |
| Catálogos | [catalogos.md](../api/catalogos.md) | 12 |
| Calidad de servicio | [calidad-servicio.md](../api/calidad-servicio.md) | 5 |
| Reservas | [reservas.md](../api/reservas.md) | 17 |
| Operación y salud | [operacion.md](../api/operacion.md) | 2 |
| **Total** | | **57** |

Una operación se cuenta por método y ruta parametrizada. La colección tendrá más de 57 peticiones porque el Runner ejecuta peticiones, y cada variante que deba probarse tendrá su propia petición.

## Decisiones y puntos de revisión

- **Rutas.** `baseUrl` contiene el origen del mock sin barra final. Añadir `/api/v1` en las peticiones de la API y conservar `/actuator/health` en la raíz.
- **Escenarios.** Incluir el éxito de cada operación, al menos un caso por código de error listado para esa operación y un caso por variante con ejemplo propio o con un resultado distinguible que figure en los criterios de aceptación HTTP. Aplicar los errores transversales a los flujos que los documentan de forma explícita. Por ejemplo, el `429` corresponde al inicio de sesión.
- **Trazabilidad.** En la descripción de cada petición, enlazar la sección Markdown e indicar si el ejemplo fue copiado del contrato o completado para el mock. Si el contrato menciona un error sin cuerpo de ejemplo, conservar el código HTTP documentado y marcar los campos deducidos como propuesta. Registrar cualquier discrepancia entre secciones antes de fijar un resultado ambiguo.
- **Selección del ejemplo.** Usar un nombre único en toda la colección, con el patrón `<modulo>.<operacion>.<escenario>` y enviarlo en `x-mock-response-name`. El estado esperado se escribe en el test a partir del contrato. Seleccionar el ejemplo por nombre permite distinguir dos respuestas con el mismo estado.
- **Seguridad.** Se propone un mock privado para la verificación en Postman. `x-api-key` autentica ante Postman y se configura con un valor local. `Authorization: Bearer {{accessToken}}` representa la credencial ficticia de Gestal donde corresponda. Los JSON compartidos contienen solo datos ficticios y valores vacíos para la clave y la URL del mock.

## Tarea 1 · Probar el formato con reservas

**Archivos previstos** — Crear `gestal.postman_collection.json` y `gestal-mock.postman_environment.json`.

- [ ] Definir el entorno `Gestal Mock` con `baseUrl` y `postmanMockApiKey` vacíos, `accessToken` ficticio, `requestId` con `31`, `reservationId` con `42` y `spaceId` con `8`. Añadir otras variables solo si una petición las usa.
- [ ] Crear dos peticiones `POST {{baseUrl}}/api/v1/reservations` con el ejemplo de entrada documentado. Guardar y seleccionar `reservas.crear.exito` con `201` y `reservas.crear.franja-ocupada` con `409` y `codigo` igual a `FRANJA_OCUPADA`.
- [ ] Añadir a cada petición un script en **Scripts → Post-response**. En el JSON de colección, guardarlo en `event` con `listen: "test"`. El caso exitoso usa `201` y el caso de conflicto usa `409`.

```javascript
pm.test("Crear reserva con franja ocupada devuelve HTTP 409", function () {
  pm.response.to.have.status(409);
});
```

- [ ] Importar los dos JSON, crear el mock a partir de la colección en el workspace de pruebas y asignar su URL a `baseUrl`. Si es privado, configurar el valor local de `postmanMockApiKey` y enviar `x-api-key: {{postmanMockApiKey}}` desde Postman.
- [ ] Ejecutar ambas peticiones con **Send** y verificar que cada una recibe el ejemplo y el código esperados. En una copia temporal, cambiar solo la aserción de `409` a `201`, conservar el selector del ejemplo y comprobar que la prueba falla. Restaurar `409` y comprobar que vuelve a pasar.

## Tarea 2 · Completar los contratos por módulo

**Archivo previsto** — Ampliar `gestal.postman_collection.json` y actualizar el entorno si aparecen variables necesarias.

- [ ] Completar las nueve carpetas con los métodos, rutas, parámetros, cabeceras y cuerpos de sus Markdown. Guardar las respuestas con el código, cabeceras y cuerpo documentados. Mantener cada escenario ejecutable por sí solo con sus datos de ejemplo.
- [ ] Añadir una aserción de código exacto en cada petición, con un nombre que identifique el escenario. Para errores de negocio con `application/problem+json`, verificar también el tipo de contenido y el campo `status` del cuerpo. Cuando el contrato dé un `codigo` específico, comprobarlo para distinguir el error de una respuesta de infraestructura con el mismo código HTTP.
- [ ] Verificar los formatos especiales. `POST /api/v1/auth/logout` devuelve `204` con cuerpo vacío. `GET /api/v1/dashboard/reports` devuelve `200` con `Content-Type: text/csv` y `Content-Disposition`. `GET /actuator/health` tiene casos `200` y `503` y conserva el formato de Actuator.
- [ ] Ejecutar por separado al menos un `400`, `401`, `403`, `404`, `409`, `429` y `503` documentado. Para los `401` y `404` de Gestal, comprobar que las aserciones sobre el cuerpo distinguen una credencial de Gestal inválida o un recurso inexistente de un fallo de acceso al mock o de una ruta sin ejemplo.
- [ ] Revisar el inventario agrupando por módulo y por método y ruta normalizada. El resultado esperado son los nueve conteos de la tabla y 57 claves únicas. Cada escenario del inventario debe tener un ejemplo seleccionable y una prueba posterior a la respuesta.

## Tarea 3 · Verificar la entrega en Postman

**Entradas** — Colección y entorno terminados, con un mock configurado en el workspace de pruebas.

- [ ] Importar ambos JSON desde cero y activar `Gestal Mock`. Configurar `baseUrl` y la clave local, y verificar que la colección importada contiene las nueve carpetas y que Postman resuelve las variables usadas.
- [ ] Ejecutar la colección completa con **Collection Runner**, usando una iteración. Confirmar que el número de peticiones ejecutadas coincide con el inventario de escenarios, que cada petición ejecutó su test de estado y que hubo cero fallos, errores de ejecución y casos omitidos.
- [ ] Repetir los dos casos de reserva en orden inverso. Ambos deben seguir pasando, porque cada uno selecciona su propio ejemplo y es independiente del orden de ejecución.
- [ ] Registrar en la entrega el total de operaciones, peticiones ejecutadas y resultados del Runner. Si la cuenta no permite crear el mock o ejecutar la colección completa, registrar la comprobación pendiente con el motivo y separar los resultados verificados localmente de los pendientes en Postman.
- [ ] Revisar los JSON finales para confirmar que la URL y la clave del mock quedaron vacías y que los tokens, cookies y datos de ejemplo son ficticios.

## Referencias de Postman

- [Formato de exportación para colecciones y entornos](https://learning.postman.com/docs/getting-started/importing-and-exporting/exporting-data)
- [Crear un Mock Server a partir de una colección](https://learning.postman.com/docs/design-apis/mock-apis/set-up-mock-servers)
- [Selección de ejemplos por encabezado](https://learning.postman.com/docs/design-apis/mock-apis/matching-algorithm/)
- [Scripts de prueba posteriores a la respuesta](https://learning.postman.com/docs/tests-and-scripts/write-scripts/test-scripts)
- [Ejecutar una colección con Collection Runner](https://learning.postman.com/docs/tests-and-scripts/running-collections/intro-to-collection-runs)
