# vacaciones-service

Microservicio de Gestión de Vacaciones del Reto 4, implementado en **C# / ASP.NET Core 8**.

## Estrategia de validación de empleado

Se usa **réplica local por eventos**. El servicio consume `empleado.creado` y `empleado.retirado` desde `vacaciones.empleados` y mantiene una tabla mínima `empleados_validos`. Así, `POST /vacaciones` no depende de una llamada REST síncrona a Empleados.

## Endpoints

- `POST /vacaciones`
- `GET /vacaciones/{id}`
- `GET /vacaciones?empleadoId={id}`
- `GET /vacaciones`
- `DELETE /vacaciones/{id}` (cambia el estado a `CANCELADA`)

## Validaciones

- `fechaFin` debe ser posterior a `fechaInicio`.
- `fechaInicio` no puede estar en el pasado.
- No puede existir solapamiento con un período `PROGRAMADA` o `EN_CURSO`.
- El empleado debe existir y estar activo en la réplica local.

Al crear un período se publica `vacaciones.programadas` en `rrhh.events`.

Swagger UI: `/vacaciones/docs`.

## Cancelación y trazabilidad

Una cancelación no elimina el período. Se conserva:

- `estado = CANCELADA`
- `motivoCancelacion`
- `fechaCancelacion`

Si el empleado se retira, los períodos `PROGRAMADA` se cancelan automáticamente con el motivo `Cancelada automáticamente por retiro del empleado`. Los períodos `EN_CURSO` no se cancelan: terminan normalmente para publicar `vacaciones.finalizadas`; `auth-service` garantiza que una cuenta retirada permanentemente no se reactive.

## Pruebas unitarias — Reto 6

Las reglas puras viven en `VacationRules.cs` y se prueban con xUnit sin Docker ni base de datos.

En Windows:

```powershell
.\run-tests.ps1
```

Reportes:

```text
reports/results.trx
reports/coverage.xml
```
