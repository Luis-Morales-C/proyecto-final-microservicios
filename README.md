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

## Decisiones técnicas

En el desarrollo del Reto 2 se tomaron tres decisiones técnicas principales. 

### 1. Motor de base de datos por servicio

Se decidió utilizar **PostgreSQL como motor de base de datos para ambos microservicios**, manteniendo una base de datos independiente para cada servicio.

- `gestion-empleados` utiliza la base de datos `gestion_empleados`.
- `gestion-departamentos` utiliza la base de datos `gestion_departamentos`.

Aunque los dos servicios utilizan el mismo motor, sus bases de datos están completamente separadas. El microservicio de empleados no accede directamente a las tablas de departamentos; cuando necesita validar un `departamentoId`, realiza una solicitud HTTP REST al microservicio `gestion-departamentos`.

**¿Qué se ganó con esta decisión?**

- Se mantiene la independencia de los microservicios.
- Se utiliza un motor conocido y adecuado para datos relacionales.
- Se simplifica la configuración y operación del proyecto al trabajar con un único motor.
- PostgreSQL permite manejar restricciones de integridad, persistencia y transacciones.
- Se facilita la ejecución del sistema mediante Docker Compose.

**¿Qué se sacrificó o costó?**

Se renunció a utilizar motores diferentes para aprovechar la persistencia políglota. Esto significa que no se explota la posibilidad de seleccionar un motor diferente según las necesidades específicas de cada servicio.

Sin embargo, para este proyecto se consideró que utilizar PostgreSQL en ambos servicios reduce la complejidad operacional y permite concentrar el esfuerzo en la comunicación entre microservicios, persistencia, validaciones y tolerancia a fallos.

La independencia se mantiene a nivel de servicio y de base de datos, aunque el motor utilizado sea el mismo.

---

### 2. Creación del esquema

Se decidió utilizar **auto-DDL mediante los ORM** para la creación del esquema de las bases de datos.

En `gestion-empleados`, el esquema es administrado mediante **Hibernate/JPA**, utilizando:

`spring.jpa.hibernate.ddl-auto=update`

En `gestion-departamentos`, el esquema es creado mediante **SQLAlchemy**, utilizando:

`Base.metadata.create_all(bind=engine)`

Esta decisión permite que las tablas necesarias sean creadas automáticamente cuando los servicios se inicializan, evitando depender de una ejecución manual de scripts SQL para levantar el proyecto desde cero.

**¿Qué se ganó con esta decisión?**

- Configuración inicial sencilla.
- El proyecto puede levantar las estructuras necesarias automáticamente.
- Se reduce la cantidad de pasos manuales para ejecutar el sistema.
- El esquema se encuentra relacionado directamente con los modelos utilizados por cada servicio.
- Facilita el desarrollo y las pruebas del Reto 2.

**¿Qué ocurre cuando el esquema tenga que cambiar y ya existan datos?**

El uso de auto-DDL es conveniente para el desarrollo y las pruebas, pero tiene limitaciones cuando el sistema evoluciona y existen datos importantes.

En un entorno de producción, no se recomienda depender de `ddl-auto=update` o de `create_all()` como estrategia principal para controlar cambios complejos del esquema. Para cambios que requieran transformaciones de datos, renombrar columnas, eliminar estructuras o mantener compatibilidad entre versiones, sería más adecuado utilizar un sistema de **migraciones versionadas**, como Flyway, Liquibase o Alembic.

Por lo tanto, se eligió auto-DDL porque simplifica el objetivo del Reto 2 y permite reproducir la creación inicial del esquema, dejando las migraciones versionadas como una mejora para una evolución posterior del sistema.

---

### 3. Garantía de unicidad

Se decidió utilizar **una combinación de validación previa en la aplicación y restricciones de unicidad en la base de datos**.

Antes de registrar un empleado, `gestion-empleados` realiza las siguientes validaciones en este orden:

1. Verifica que el `email` no exista.
2. Verifica que el `numeroEmpleado` no exista.
3. Consulta mediante REST al microservicio `gestion-departamentos` para comprobar que el departamento exista.
4. Si todas las validaciones son correctas, registra el empleado.

Además, la base de datos mantiene las restricciones de unicidad correspondientes para evitar duplicados a nivel de persistencia.

La validación previa permite proporcionar una respuesta clara al usuario, mientras que la restricción `UNIQUE` de la base de datos funciona como garantía definitiva de integridad.

**¿Qué ocurre si dos peticiones con el mismo email llegan al mismo tiempo?**

Una consulta previa por sí sola no garantiza la unicidad en un escenario concurrente. Por ejemplo, dos peticiones podrían consultar al mismo tiempo y ambas comprobar que el correo todavía no existe.

Por esta razón, la restricción `UNIQUE` de la base de datos es necesaria como última barrera. Si dos peticiones concurrentes intentan guardar el mismo `email`, PostgreSQL permite que solo una operación cumpla la restricción y rechaza la otra.

La aplicación captura el error de integridad generado por la base de datos y lo transforma en una respuesta controlada para el cliente.

De esta manera se obtiene:

- Validación previa para proporcionar mensajes claros.
- Restricción de base de datos para garantizar la integridad.
- Protección frente a condiciones de carrera.
- Respuesta `400 Bad Request` cuando se intenta registrar un email o `numeroEmpleado` duplicado.

