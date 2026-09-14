# Proyecto Final - Microservicios

## Descripción

Sistema distribuido basado en una arquitectura de microservicios, desarrollado de manera incremental a través de los retos del curso.

En el Reto 1 se construyó el microservicio `gestion-empleados`, encargado del registro y consulta básica de empleados, utilizando almacenamiento en memoria.

En el Reto 2 el sistema evolucionó hacia una arquitectura distribuida con dos servicios de negocio independientes, bases de datos PostgreSQL separadas, persistencia mediante volúmenes Docker, comunicación REST, configuración mediante variables de entorno, health checks y documentación OpenAPI.

## Arquitectura

El proyecto estará compuesto por múltiples microservicios independientes, un API Gateway, un message broker, bases de datos y herramientas de observabilidad.

En el estado alcanzado en el Reto 2 se encuentran implementados los siguientes componentes:

```text
Cliente HTTP
     |
     +-----------------------------+
     |                             |
 localhost:8080              localhost:8000
     |                             |
gestion-empleados --------HTTP----> gestion-departamentos
 Java 21 / Spring                  Python 3.11 / FastAPI
     |                             |
     | SQL                         | SQL
     v                             v
database-empleados          database-departamentos
 PostgreSQL                    PostgreSQL
     |                             |
 empleados-db-data       departamentos-db-data

El proyecto estará compuesto por múltiples microservicios independientes, un API Gateway, un message broker, bases de datos y herramientas de observabilidad.

En el estado alcanzado en el Reto 2 se encuentran implementados los siguientes componentes:

```text
Cliente HTTP
     |
     +-----------------------------+
     |                             |
 localhost:8080              localhost:8000
     |                             |
gestion-empleados --------HTTP----> gestion-departamentos
 Java 21 / Spring                  Python 3.11 / FastAPI
     |                             |
     | SQL                         | SQL
     v                             v
database-empleados          database-departamentos
 PostgreSQL                    PostgreSQL
     |                             |
 empleados-db-data       departamentos-db-data
```

Todos los servicios se comunican dentro de la red Docker `microservices-network`.

`gestion-empleados` no accede directamente a la base de datos de departamentos. Cuando necesita validar un `departamentoId`, realiza una petición HTTP al microservicio `gestion-departamentos`.

## Microservicios

[svg](https://github.com/Luis-Morales-C/proyecto-final-microservicios/blob/main/README.md#microservicios)

| **MicroservicioTecnologíaEstado** |                       |                                                                                                                                                                |
| --------------------------------- | --------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Gestión de empleados              | Spring Boot + Java 21 | 🟢 Reto 1 y Reto 2 completos — [Ver documentación](microservicios/gestion-empleados/README.md) |
| Gestión de departamentos          | Python 3.11 + FastAPI | 🟢 Reto 2 completo — [Ver documentación](microservicios/gestion-departamentos/README.md) |
| Autenticación                     | Pendiente             | 🔴                                                                                                                                                             |
| Gestión de perfiles               | Pendiente             | 🔴                                                                                                                                                             |
| Gestión de vacaciones             | Pendiente             | 🔴                                                                                                                                                             |
| Notificaciones                    | Pendiente             | 🔴                                                                                                                                                             |
| API Gateway                       | Pendiente             | 🔴                                                                                                                                                             |

## Tecnologías

[svg](https://github.com/Luis-Morales-C/proyecto-final-microservicios/blob/main/README.md#tecnolog%C3%ADas)

Las tecnologías utilizadas hasta el Reto 2 son:

| Tecnología | Uso |
|---|---|
| Java 21 | Lenguaje de `gestion-empleados` |
| Spring Boot 4.1.0 | Framework de `gestion-empleados` |
| Spring Data JPA | Persistencia de empleados |
| Hibernate | Implementación ORM y generación/actualización del esquema de empleados |
| PostgreSQL 18 | Base de datos de empleados y departamentos |
| Python 3.11 | Lenguaje de `gestion-departamentos` |
| FastAPI | API REST de departamentos |
| Pydantic | Validación de datos y esquemas OpenAPI |
| SQLAlchemy | Persistencia de departamentos |
| psycopg2-binary | Conexión de Python con PostgreSQL |
| Docker | Contenerización |
| Docker Compose | Orquestación de servicios |
| Springdoc OpenAPI | Swagger/OpenAPI de empleados |
| Pytest | Pruebas automatizadas de departamentos |

## Ejecución

[svg](https://github.com/Luis-Morales-C/proyecto-final-microservicios/blob/main/README.md#ejecuci%C3%B3n)

La solución del Reto 2 puede levantarse desde la raíz del proyecto mediante un único comando:

```text
docker compose up --build
```

Para detener el sistema conservando los volúmenes:

```text
docker compose down
```

Para detener el sistema y eliminar también los volúmenes:

```text
docker compose down -v
```

La configuración de las bases de datos se encuentra preparada mediante variables de entorno. El archivo `.env.example` sirve como referencia de configuración y las credenciales reales no deben versionarse en Git.

### Verificación de arranque ordenado



El proyecto utiliza `healthcheck` en los servicios de base de datos y en el microservicio de gestión de departamentos. Además, `gestion-empleados` utiliza `depends_on` con la condición `service_healthy`, garantizando que sus dependencias estén disponibles antes de iniciar.

Para levantar el proyecto desde cero se ejecuta:

```text
docker compose up --build
```

Luego se puede verificar el estado de los contenedores mediante:

```text
docker compose ps
```

La salida permite comprobar que los servicios que funcionan como dependencias se encuentran en estado `healthy`, especialmente:

- `database-empleados`
- `database-departamentos`
- `gestion-departamentos`

Esto evidencia el arranque ordenado de los servicios y la verificación de disponibilidad mediante los `healthcheck` configurados en Docker Compose.

**Evidencia:** colocar aquí la captura real de `docker compose ps`.

**Ruta de la evidencia:**

```text
docs/evidencias/reto2/docker/R2-02-compose-ps.png
```

### Infraestructura Docker

El `docker-compose.yml` define:

- `gestion-empleados`
- `gestion-departamentos`
- `database-empleados`
- `database-departamentos`
- red interna `microservices-network`
- volumen `empleados-db-data`
- volumen `departamentos-db-data`
- variables de entorno
- health checks
- dependencias con `condition: service_healthy`

Cada servicio de negocio posee su propio Dockerfile.

### Persistencia de datos

Cada microservicio administra su propia base de datos PostgreSQL y su propio volumen.

Los datos deben sobrevivir al ciclo:

```text
docker compose down
docker compose up -d
```

y deben eliminarse cuando se utiliza:

```text
docker compose down -v
```

**Evidencia de persistencia después de `down`:**

```text
docs/evidencias/reto2/docker/R2-05-persistencia-down.png
```

**Evidencia de eliminación después de `down -v`:**

```text
docs/evidencias/reto2/docker/R2-06-persistencia-down-v.png
```

### Creación reproducible del esquema

Para el Reto 2 se utiliza auto-DDL.

En empleados:

```text
spring.jpa.hibernate.ddl-auto=update
```

Hibernate crea o actualiza el esquema de la entidad `Empleado`.

En departamentos:

```text
Base.metadata.create_all(bind=engine)
```

SQLAlchemy crea las tablas al iniciar la aplicación cuando todavía no existen.

Esta decisión evita pasos manuales adicionales al levantar las bases de datos desde cero. Los archivos `scripts/init-db.sql` y `scripts/seed-data.sql` permanecen como parte de la estructura general, pero no constituyen el mecanismo activo de creación del esquema en este reto.

### Comunicación entre servicios

La comunicación entre empleados y departamentos se realiza mediante HTTP REST.

Dentro de Docker se utiliza:

```text
http://gestion-departamentos:8000
```

La validación de un departamento se realiza mediante:

```text
GET http://gestion-departamentos:8000/departamentos/{id}
```

Esto garantiza que cada microservicio sea propietario exclusivamente de sus propios datos.

**Evidencia de comunicación REST:**

```text
docs/evidencias/reto2/integracion/R2-18-comunicacion-rest.png
```

### Validaciones del registro de empleados

El flujo implementado para registrar un empleado es:

1. Verificar que el email no exista.
2. Verificar que el `numeroEmpleado` no exista.
3. Consultar el departamento mediante REST.
4. Si el departamento existe, establecer `estado = ACTIVO`.
5. Guardar el empleado en PostgreSQL.
6. Si PostgreSQL detecta una violación de unicidad por concurrencia, convertirla en una respuesta controlada.

Las tres validaciones evaluables del Reto 2 son:

| Caso | Resultado esperado |
|---|---|
| Email duplicado | `400 Bad Request` |
| `numeroEmpleado` duplicado | `400 Bad Request` |
| Departamento inexistente | `400 Bad Request` |

**Evidencias:**

```text
docs/evidencias/reto2/postman/R2-12-email-duplicado.png
docs/evidencias/reto2/postman/R2-13-numero-duplicado.png
docs/evidencias/reto2/postman/R2-14-departamento-no-existe.png
```

### Timeout, reintentos y fallo parcial

`gestion-empleados` incorpora protección para las llamadas al servicio de departamentos:

| Mecanismo | Configuración |
|---|---|
| Timeout de conexión | 2 segundos |
| Timeout de lectura | 2 segundos |
| Máximo de intentos | 4 |
| Esperas | 1 s, 2 s y 4 s |
| Resultado después del agotamiento | `503 Service Unavailable` |

Cuando departamentos permanece indisponible, el empleado no se guarda.

**Evidencia:**

```text
docs/evidencias/reto2/integracion/R2-19-timeout-retry-503.png
```

### Swagger / OpenAPI


`gestion-empleados`:

```text
Swagger UI:
http://localhost:8080/swagger-ui.html

OpenAPI:
http://localhost:8080/v3/api-docs
```

`gestion-departamentos`:

```text
Swagger UI:
http://localhost:8000/docs

ReDoc:
http://localhost:8000/redoc

OpenAPI:
http://localhost:8000/openapi.json
```

**Evidencias:**

```text
docs/evidencias/reto2/swagger/R2-20-swagger-empleados.png
docs/evidencias/reto2/swagger/R2-21-swagger-departamentos.png
```

### Evidencias del Reto 1



El Reto 1 corresponde al estado histórico del servicio `gestion-empleados`, cuando los empleados se almacenaban en memoria mediante `ConcurrentHashMap`.



**Ubicación:**

```text
docs/evidencias/reto1/
```



```text
R1-01-registro-empleado.png
R1-02-email-duplicado.png
R1-03-numero-duplicado.png
R1-04-get-empleado.png
R1-05-empleado-no-existe.png
R1-06-ruta-no-soportada.png
R1-07-docker.png
```

### Evidencias del Reto 2


```text
docs/evidencias/reto2/
```

La estructura recomendada es:

```text
docs/evidencias/reto2/
├── docker/
├── postman/
├── swagger/
└── integracion/
```


## Estado al finalizar el Reto 2


El Reto 2 deja implementados:

- Dos servicios de negocio independientes.
- Java 21 + Spring Boot para empleados.
- Python 3.11 + FastAPI para departamentos.
- Una base de datos PostgreSQL independiente por servicio.
- Volúmenes Docker independientes.
- Red interna Docker.
- Variables de entorno.
- Health checks.
- `depends_on` con `service_healthy`.
- Persistencia mediante PostgreSQL.
- Comunicación REST entre servicios.
- Validación de existencia del departamento.
- Unicidad de email y `numeroEmpleado`.
- Timeout y reintentos.
- Swagger/OpenAPI en ambos servicios.
- Dockerfile por servicio.
- Despliegue mediante `docker compose up --build`.

Las funcionalidades que la consigna reserva para retos posteriores, como PUT, DELETE, eventos, Circuit Breaker y los demás microservicios, permanecen pendientes y no se presentan como implementadas en el Reto 2.
