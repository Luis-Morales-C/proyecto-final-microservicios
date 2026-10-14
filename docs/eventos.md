# Catálogo implementado de eventos (Retos 4 y 5)

> Este archivo documenta **lo que implementa este repositorio**. Los nombres y cargas útiles normativas deben mantenerse alineados con el Catálogo de Eventos entregado por el curso.

Todos los eventos se publican en el exchange topic `rrhh.events` y usan el envelope común:

```json
{
  "id": "uuid-del-evento",
  "type": "nombre.del.evento",
  "version": 1,
  "occurredAt": "2026-10-13T15:00:00Z",
  "producer": "servicio-productor",
  "data": {}
}
```

El campo `id` se usa para deduplicación en los consumidores. Cada consumidor mantiene su propia tabla de eventos procesados y descarta un `id` ya aplicado.

## Eventos de empleados

| Evento | Productor | Consumidores actuales | Efecto principal |
|---|---|---|---|
| `empleado.creado` | `gestion-empleados` | Perfiles, Vacaciones, Auth y Notificaciones | Crear/sincronizar perfil, réplica local de empleado, cuenta INACTIVA y contacto de notificaciones |
| `empleado.actualizado` | `gestion-empleados` | Perfiles | Sincronizar nombre y email del perfil |
| `empleado.retirado` | `gestion-empleados` | Perfiles, Vacaciones, Auth y Notificaciones | Archivar perfil, invalidar empleado para nuevas vacaciones, cancelar vacaciones PROGRAMADA y desactivar cuenta permanentemente |

`empleado.retirado` no borra al empleado. El registro permanece en Empleados con `estado=RETIRADO` y `fechaRetiro` para auditoría.

## Eventos de vacaciones

| Evento | Productor | Consumidores actuales | Efecto principal |
|---|---|---|---|
| `vacaciones.programadas` | `vacaciones-service` | Notificaciones | Confirmación del período programado |
| `vacaciones.iniciadas` | `vacaciones-service` | Auth y Notificaciones | Suspender temporalmente la cuenta y notificar |
| `vacaciones.finalizadas` | `vacaciones-service` | Auth y Notificaciones | Reactivar solo si la cuenta estaba suspendida temporalmente y notificar |

El scheduler de `vacaciones-service` realiza las transiciones:

```text
PROGRAMADA -> EN_CURSO -> FINALIZADA
```

Una vacación `PROGRAMADA` de un empleado retirado se cancela lógicamente y conserva `motivoCancelacion` y `fechaCancelacion`. Una vacación que ya está `EN_CURSO` se deja finalizar para cubrir el caso borde obligatorio del Reto 5: `vacaciones.finalizadas` no puede reactivar una cuenta que ya quedó `DESACTIVADA_PERMANENTE`.

## Eventos de autenticación

| Evento | Productor | Consumidor | Efecto principal |
|---|---|---|---|
| `usuario.creado` | `auth-service` | Notificaciones | Simular correo de activación con token |
| `usuario.recuperacion` | `auth-service` | Notificaciones | Simular correo de recuperación |
| `cuenta.activada` | `auth-service` | Notificaciones | Informar activación inicial o fin de vacaciones |
| `cuenta.desactivada` | `auth-service` | Notificaciones | Informar suspensión por vacaciones o baja por retiro |

## Colas y bindings de RabbitMQ

| Cola | Routing keys principales |
|---|---|
| `perfiles.empleados` | `empleado.creado`, `empleado.actualizado`, `empleado.retirado` |
| `vacaciones.empleados` | `empleado.creado`, `empleado.retirado` |
| `notificaciones.empleados` | `empleado.creado`, `empleado.retirado` |
| `notificaciones.vacaciones` | `vacaciones.programadas`, `vacaciones.iniciadas`, `vacaciones.finalizadas` |
| `auth.eventos` | `empleado.creado`, `empleado.retirado`, `vacaciones.iniciadas`, `vacaciones.finalizadas` |
| `notificaciones.seguridad` | `usuario.creado`, `usuario.recuperacion`, `cuenta.activada`, `cuenta.desactivada` |

La infraestructura se declara en `rabbitmq/definitions.json` y `rabbitmq-init` la importa automáticamente después de que RabbitMQ está saludable.
