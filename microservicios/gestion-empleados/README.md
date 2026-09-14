# Gestión de Empleados - Reto 1 y Reto 2

## Descripción

`gestion-empleados` es el primer microservicio del proyecto final. Su desarrollo comenzó en el Reto 1 como un servidor web para registrar y consultar empleados y posteriormente evolucionó en el Reto 2 hacia una solución persistente integrada con el microservicio de departamentos.

El contenido del Reto 1 se conserva como antecedente del flujo de trabajo. La sección del Reto 2 documenta la evolución realizada.

# Reto 1 - Servidor Web para Gestión Básica de Empleados

## Objetivo

Construir un servidor web capaz de registrar empleados, consultarlos por su identificador y responder correctamente ante errores de duplicidad, rutas inexistentes y métodos HTTP no soportados.

En este reto no se utilizaba una base de datos. Los empleados se almacenaban en memoria mediante `ConcurrentHashMap`, por lo que los datos se perdían al reiniciar el servicio.
# Reto 1 - Servidor Web para Gestión Básica de Empleados

[svg](https://github.com/Luis-Morales-C/proyecto-final-microservicios/blob/main/microservicios/gestion-empleados/README.md#reto-1---servidor-web-para-gestión-básica-de-empleados)

## Objetivo

Construir un servidor web capaz de registrar empleados, consultarlos por su identificador y responder correctamente ante errores de duplicidad, rutas inexistentes y métodos HTTP no soportados.

En este reto no se utilizaba una base de datos. Los empleados se almacenaban en memoria mediante `ConcurrentHashMap`, por lo que los datos se perdían al reiniciar el servicio.

## Tecnologías utilizadas

| Tecnología | Uso |
|---|---|
| Java 21 | Lenguaje de programación |
| Spring Boot 4.1.0 | Framework del servicio web |
| Maven | Gestión de dependencias y compilación |
| Docker | Contenerización |

## Modelo canónico de Empleado

El modelo utilizado en el Reto 1 contiene los siguientes 10 campos:

| Campo | Descripción |
|---|---|
| `id` | Identificador del empleado |
| `nombre` | Nombre |
| `apellido` | Apellido |
| `email` | Correo electrónico |
| `numeroEmpleado` | Número único del empleado |
| `cargo` | Cargo |
| `area` | Área |
| `departamentoId` | Identificador del departamento |
| `fechaIngreso` | Fecha de ingreso |
| `estado` | Estado del empleado |

Los estados contemplados son `ACTIVO`, `EN_VACACIONES` y `RETIRADO`. En el alcance del Reto 1 se utilizaba `ACTIVO`.

## Estructura del Reto 1

```text
gestion-empleados/
├── src/main/java/co/edu/uniquindio/gestionempleados/
│   ├── GestionEmpleadosApplication.java
│   ├── controller/
│   │   └── EmpleadoController.java
│   ├── service/
│   │   └── EmpleadoService.java
│   ├── repository/
│   │   └── EmpleadoRepository.java
│   ├── model/
│   │   ├── Empleado.java
│   │   └── EstadoEmpleado.java
│   └── exception/
│       ├── EmpleadoDuplicadoException.java
│       ├── EmpleadoNoEncontradoException.java
│       └── GlobalExceptionHandler.java
├── Dockerfile
└── pom.xml
```

## Endpoints del Reto 1

### POST `/empleados`

Registra un empleado.

Resultado esperado:

```text
200 OK
```

Validaciones:

- Email duplicado → `400 Bad Request`.
- `numeroEmpleado` duplicado → `400 Bad Request`.

### GET `/empleados/{id}`

Consulta un empleado por su ID.

Resultados:

- Empleado existente → `200 OK`.
- Empleado inexistente → `404 Not Found`.

### Ruta inexistente

Una ruta no soportada debe devolver:

```text
404 Not Found
```

con el mensaje:

```text
Recurso no encontrado
```

## Evidencias del Reto 1

```text
docs/evidencias/reto1/
```

### R1-01 - Registro exitoso

```text
POST http://localhost:8080/empleados
```

Resultado esperado:

```text
200 OK
```

**Imagen:**

```text
docs/evidencias/reto1/R1-01-registro-empleado.png
```

### R1-02 - Email duplicado

Resultado esperado:

```text
400 Bad Request
```

**Imagen:**

```text
docs/evidencias/reto1/R1-02-email-duplicado.png
```

### R1-03 - NumeroEmpleado duplicado

Resultado esperado:

```text
400 Bad Request
```

**Imagen:**

```text
docs/evidencias/reto1/R1-03-numero-duplicado.png
```

### R1-04 - Consulta de empleado existente

```text
GET http://localhost:8080/empleados/E001
```

Resultado esperado:

```text
200 OK
```

**Imagen:**

```text
docs/evidencias/reto1/R1-04-get-empleado.png
```

### R1-05 - Consulta de empleado inexistente

```text
GET http://localhost:8080/empleados/E999
```

Resultado esperado:

```text
404 Not Found
```

**Imagen:**

```text
docs/evidencias/reto1/R1-05-empleado-no-existe.png
```

### R1-06 - Ruta no soportada

Resultado esperado:

```text
404 Not Found
Recurso no encontrado
```

**Imagen:**

```text
docs/evidencias/reto1/R1-06-ruta-no-soportada.png
```

### R1-07 - Ejecución mediante Docker

**Imagen:**

```text
docs/evidencias/reto1/R1-07-docker.png
```

# Reto 2 - Evolución a Persistencia y Microservicios

[svg](https://github.com/Luis-Morales-C/proyecto-final-microservicios/blob/main/microservicios/gestion-empleados/README.md#reto-2---evolución-a-persistencia-y-microservicios)

## Evolución realizada

En el Reto 2 el servicio deja de almacenar empleados en memoria y pasa a utilizar PostgreSQL mediante Spring Data JPA y Hibernate.

La organización por capas del Reto 1 se conserva y se incorpora una capa de persistencia real mediante `JpaRepository`.

El modelo canónico completo permanece sin cambios: se conservan los 10 campos, incluido `estado`.

## Tecnologías utilizadas en el Reto 2

| Tecnología | Uso |
|---|---|
| Java 21 | Lenguaje |
| Spring Boot 4.1.0 | Framework |
| Spring MVC | Endpoints REST |
| Spring Validation | Validación de entrada |
| Spring Data JPA | Persistencia |
| Hibernate | ORM y esquema |
| PostgreSQL 18 | Base de datos |
| RestClient | Comunicación con departamentos |
| Springdoc OpenAPI | Swagger/OpenAPI |
| Maven | Dependencias y compilación |
| Docker | Contenerización |

## Persistencia

La entidad `Empleado` utiliza JPA y se almacena en PostgreSQL.

El repositorio utiliza:

```text
JpaRepository<Empleado, String>
```

La configuración de Hibernate utiliza:

```text
spring.jpa.hibernate.ddl-auto=update
```

Esto permite crear o actualizar el esquema automáticamente al iniciar el servicio.

## Configuración mediante variables de entorno

La conexión a PostgreSQL utiliza:

```text
DB_HOST
DB_PORT
DB_NAME
DB_USER
DB_PASSWORD
```

La URL del servicio de departamentos utiliza:

```text
DEPARTAMENTOS_URL
```

Dentro de Docker:

```text
DEPARTAMENTOS_URL=http://gestion-departamentos:8000
```

No se utiliza `localhost` para la comunicación entre contenedores.

## Comunicación con Gestión de Departamentos

Antes de guardar un empleado, el servicio valida:

1. Email.
2. `numeroEmpleado`.
3. Existencia del departamento mediante HTTP REST.

La consulta interna es:

```text
GET http://gestion-departamentos:8000/departamentos/{id}
```

Si el departamento no existe, el empleado no se guarda y se devuelve:

```text
400 Bad Request
```

## Validación de unicidad

El servicio utiliza dos mecanismos:

```text
Consulta previa
      +
Restricción UNIQUE en PostgreSQL
```

Para el email y `numeroEmpleado` se realizan consultas previas y además existen restricciones de unicidad en la base de datos.

Esto protege también el caso de concurrencia. Si dos solicitudes pasan la consulta previa al mismo tiempo, PostgreSQL puede rechazar una inserción mediante la restricción `UNIQUE`. La excepción `DataIntegrityViolationException` se transforma en una respuesta controlada.

## Timeout y reintentos

La comunicación con departamentos utiliza:

| Mecanismo | Configuración |
|---|---|
| Timeout de conexión | 2 segundos |
| Timeout de lectura | 2 segundos |
| Máximo de intentos | 4 |
| Espera creciente | 1 s, 2 s, 4 s |
| Agotamiento | `503 Service Unavailable` |

Si departamentos no responde después de los intentos configurados, el empleado no se guarda.

## Endpoints vigentes en el Reto 2

### POST `/empleados`

Registra un empleado.

Resultado exitoso:

```text
201 Created
```

Debe conservar los 10 campos del modelo y devolver `estado = ACTIVO` al registrar.

Validaciones:

```text
Email duplicado             → 400
numeroEmpleado duplicado   → 400
Departamento inexistente   → 400
Departamento no disponible → 503
```

### GET `/empleados/{id}`

```text
200 OK → empleado encontrado
404 Not Found → empleado inexistente
```

### GET `/empleados`

Devuelve la lista de empleados:

```text
200 OK
```

## Ejemplo de empleado

```json
{
  "id": "E001",
  "nombre": "Juan",
  "apellido": "Perez",
  "email": "juan.perez@empresa.com",
  "numeroEmpleado": "EMP-2026-001",
  "cargo": "Desarrollador",
  "area": "Tecnologia",
  "departamentoId": "IT",
  "fechaIngreso": "2026-02-10",
  "estado": "ACTIVO"
}
```

## Swagger / OpenAPI

[svg](https://github.com/Luis-Morales-C/proyecto-final-microservicios/blob/main/microservicios/gestion-empleados/README.md#swagger--openapi)

Swagger UI:

```text
http://localhost:8080/swagger-ui.html
```

OpenAPI:

```text
http://localhost:8080/v3/api-docs
```

**Evidencia:**

```text
docs/evidencias/reto2/swagger/R2-20-swagger-empleados.png
```

## Evidencias del Reto 2

### R2-01 - Registro exitoso

```text
POST http://localhost:8080/empleados
```

Resultado esperado:

```text
201 Created
```

La respuesta debe mostrar los 10 campos y:

```text
estado = ACTIVO
```

**Imagen:**

```text
docs/evidencias/reto2/postman/R2-11-crear-empleado.png
```

### R2-02 - Email duplicado

Resultado esperado:

```text
400 Bad Request
```

**Imagen:**

```text
docs/evidencias/reto2/postman/R2-12-email-duplicado.png
```

### R2-03 - NumeroEmpleado duplicado

Resultado esperado:

```text
400 Bad Request
```

**Imagen:**

```text
docs/evidencias/reto2/postman/R2-13-numero-duplicado.png
```

### R2-04 - Departamento inexistente

Resultado esperado:

```text
400 Bad Request
```

**Imagen:**

```text
docs/evidencias/reto2/postman/R2-14-departamento-no-existe.png
```

### R2-05 - Consulta por ID

```text
GET http://localhost:8080/empleados/E001
```

Resultado esperado:

```text
200 OK
```

**Imagen:**

```text
docs/evidencias/reto2/postman/R2-15-get-empleado.png
```

### R2-06 - Listado

```text
GET http://localhost:8080/empleados
```

Resultado esperado:

```text
200 OK
```

**Imagen:**

```text
docs/evidencias/reto2/postman/R2-16-listar-empleados.png
```

### R2-07 - Empleado inexistente

Resultado esperado:

```text
404 Not Found
```

**Imagen:**

```text
docs/evidencias/reto2/postman/R2-17-empleado-no-existe.png
```

### R2-08 - Comunicación REST

La evidencia debe demostrar que `gestion-empleados` valida el departamento mediante el servicio de departamentos y no mediante acceso directo a su base de datos.

**Imagen:**

```text
docs/evidencias/reto2/integracion/R2-18-comunicacion-rest.png
```

### R2-09 - Timeout, reintentos y 503

Detener temporalmente `gestion-departamentos` y realizar un registro válido.

Resultado esperado después de los reintentos:

```text
503 Service Unavailable
```

**Imagen:**

```text
docs/evidencias/reto2/integracion/R2-19-timeout-retry-503.png
```

### R2-10 - Swagger empleados

**Imagen:**

```text
docs/evidencias/reto2/swagger/R2-20-swagger-empleados.png
```

## Estado final del servicio



Al finalizar el Reto 2, `gestion-empleados` cuenta con:

- Persistencia PostgreSQL.
- Spring Data JPA y Hibernate.
- Modelo canónico de 10 campos.
- Validación de email.
- Validación de `numeroEmpleado`.
- Validación de existencia de departamento.
- Comunicación REST.
- Timeout y reintentos.
- Restricciones `UNIQUE`.
- Endpoints POST y GET.
- Swagger/OpenAPI.
- Dockerfile.
- Configuración mediante variables de entorno.

Las funcionalidades reservadas para retos posteriores no se presentan como parte de este alcance.
