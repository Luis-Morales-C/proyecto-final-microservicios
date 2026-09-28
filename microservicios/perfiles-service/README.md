# perfiles-service

Microservicio de Gestión de Perfiles del Reto 4, implementado en **Go**.

- Consume `empleado.creado`, `empleado.actualizado` y `empleado.retirado` desde `perfiles.empleados`.
- Crea el perfil por defecto, sincroniza `nombre` y `email`, y archiva el perfil en la baja lógica.
- Deduplica por `id` del envelope en la tabla `eventos_procesados`.
- Expone `GET /perfiles`, `GET /perfiles/{empleadoId}` y `PUT /perfiles/{empleadoId}`.
- OpenAPI: `/perfiles/openapi.json`.
- Swagger UI: `/perfiles/docs`.
