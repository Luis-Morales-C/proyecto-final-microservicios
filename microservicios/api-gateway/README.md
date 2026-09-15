# API Gateway

Punto de entrada único del ecosistema de microservicios (Reto 3).

## Tecnología y justificación

Se eligió **Spring Cloud Gateway** (Gateway de aplicación) en lugar de un enrutador
declarativo (Traefik/Nginx) porque el roadmap del curso exige lógica propia en el
borde del sistema que un enrutador declarativo no ofrece sin plugins:

- **Reto 5**: el Gateway deberá validar el JWT y aplicar reglas de autorización.
- **Reto 10**: deberá validar tokens por JWKS y propagar la identidad del usuario
  como cabeceras a los servicios internos.
- **Proyecto final**: composición de respuestas (combinar empleado + departamento).

Al estar en el mismo ecosistema Java que `gestion-empleados`, se reutiliza
experiencia y tooling del equipo. Traefik se incorporará más adelante (Reto 11)
como balanceador de carga *delante* de este Gateway, no como reemplazo.

## URL base del sistema

```
http://localhost:8080
```

A partir de este reto, todos los clientes (curl, Postman/Bruno, la suite de
pruebas del Reto 6, Prometheus en el Reto 8) deben apuntar a esta única URL.
Los puertos internos (8081 para empleados, 8082 para departamentos) ya **no**
son accesibles desde fuera de la red de Docker.

## Tabla de rutas

| Ruta externa (Gateway) | Servicio interno       | URL interna                        |
|-------------------------|--------------------------|---------------------------------------|
| `/empleados/**`         | gestion-empleados        | `http://gestion-empleados:8081`      |
| `/departamentos/**`     | gestion-departamentos    | `http://gestion-departamentos:8082`  |
| `/health`               | Gateway (propio)         | —                                       |

El Gateway no reescribe el path (no hay `StripPrefix`): las rutas internas de
los servicios no cambiaron desde el Reto 2, solo dejaron de ser accesibles
directamente desde el host.

## Manejo de errores

El Gateway distingue dos tipos de error y responde distinto para cada uno:

**Ruta que no existe** (ej. `GET /ruta`, que no matchea ninguna definida
arriba) → `404 Not Found`:

```json
{
  "timestamp": "2026-09-14T20:00:00Z",
  "status": 404,
  "error": "Not Found",
  "message": "La ruta solicitada no existe en este API Gateway.",
  "path": "/ruta"
}
```

**Ruta válida, pero el servicio destino no responde** (caído, timeout de
conexión, host no resuelto dentro de la red de Docker) → `503 Service
Unavailable`, sin dejar pasar el stack trace ni la página de error por
defecto de Spring:

```json
{
  "timestamp": "2026-09-14T20:00:00Z",
  "status": 503,
  "error": "Service Unavailable",
  "message": "El servicio solicitado no está disponible en este momento. Intente nuevamente más tarde.",
  "path": "/departamentos"
}
```

Ambos casos son manejados por `GatewayErrorHandler`, que revisa la causa raíz
de la excepción: si es un fallo real de conexión (`ConnectException`,
`UnknownHostException`, `TimeoutException`, cierre prematuro de conexión) usa
503; si Spring ya trae un status explícito (como el 404 de ruta no
encontrada), lo respeta tal cual.

Timeouts configurados en `application.yml`:

| Parámetro                                          | Valor   |
|------------------------------------------------------|---------|
| `spring.cloud.gateway.httpclient.connect-timeout`     | 3000 ms |
| `spring.cloud.gateway.httpclient.response-timeout`    | 5 s     |

## Health check

```
curl http://localhost:8080/health
```

Expuesto vía Spring Boot Actuator con `management.endpoints.web.base-path: "/"`
para que quede en `/health` y no en `/actuator/health`.

## Variables de entorno

Definidas en el `.env` de la raíz del proyecto y pasadas por `docker-compose.yml`:

| Variable            | Valor                                |
|----------------------|-----------------------------------------|
| `EMPLEADOS_URL`      | `http://gestion-empleados:8081`         |
| `DEPARTAMENTOS_URL`  | `http://gestion-departamentos:8082`     |

## Cómo correrlo localmente (sin Docker)

```bash
cd microservicios/api-gateway
mvn spring-boot:run
```

Si corres el Gateway suelto en tu máquina mientras los microservicios
corren en Docker con sus puertos publicados temporalmente, exporta:

```
EMPLEADOS_URL=http://localhost:8081
DEPARTAMENTOS_URL=http://localhost:8082
```

## Pruebas manuales

```bash
# 1. A través del Gateway: debe funcionar
curl http://localhost:8080/departamentos
curl http://localhost:8080/empleados

# 2. Acceso directo a un microservicio: DEBE FALLAR (conexión rechazada,
#    porque ya no publican puertos al host, solo expose:)
curl http://localhost:8081/empleados
curl http://localhost:8082/departamentos

# 3. Health propio del Gateway
curl http://localhost:8080/health

# 4. Ruta inexistente: debe dar 404 (no 503)
curl -i http://localhost:8080/ruta

# 5. Simular caída de un servicio y validar el 503 con JSON
docker compose stop gestion-departamentos
curl -i http://localhost:8080/departamentos
docker compose start gestion-departamentos
```

## Evidencia: verificación con `docker compose ps` / `docker ps`

Solo `api-gateway` debe mostrar un mapeo `0.0.0.0:8080->8080/tcp`. Los
servicios de negocio (`gestion-empleados`, `gestion-departamentos`) deben
mostrar únicamente su puerto interno (`8081/tcp`, `8082/tcp`), sin ningún
`0.0.0.0:` — confirmando que no son alcanzables desde el host.

