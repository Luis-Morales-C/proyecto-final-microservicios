# Proyecto Final - Microservicios

## Estado actual

El repositorio contiene la evolución acumulada de los Retos 1 a 6. Los Retos 1 a 5 están implementados a nivel funcional; el Reto 6 incorpora la estrategia de pruebas automatizadas y requiere ejecutar las suites en la máquina del equipo para generar las evidencias finales.

Documentos de referencia del repositorio:

- [`docs/arquitectura.md`](docs/arquitectura.md): arquitectura actual.
- [`docs/eventos.md`](docs/eventos.md): eventos, colas y consumidores implementados.
- [`.env.example`](.env.example): variables necesarias para levantar el ecosistema.

## Arquitectura actual

El cliente accede únicamente por el API Gateway en `http://localhost:8080`. Los puertos internos de los microservicios no se publican al host. RabbitMQ expone solo su interfaz administrativa en `http://localhost:15672`.

| Servicio | Lenguaje / stack | Puerto interno | Función |
|---|---|---:|---|
| API Gateway | Java 21 / Spring Cloud Gateway | 8080 | Punto de entrada, validación JWT, RBAC/propiedad y enrutamiento |
| Gestión de empleados | Java 21 / Spring Boot | 8081 | Empleados, auditoría, eventos y Circuit Breaker |
| Gestión de departamentos | Python / FastAPI | 8082 | Departamentos |
| Notificaciones | Node.js / Express | 8083 | Consumidor reactivo e historial de notificaciones |
| Perfiles | Go | 8084 | Perfil sincronizado por eventos + REST |
| Vacaciones | C# / ASP.NET Core 8 | 8085 | Períodos, scheduler y eventos |
| Auth | C# / ASP.NET Core 8 | 8086 | Identidad, contraseñas y JWT |

Cada servicio persistente posee su propia base PostgreSQL. La comunicación síncrona entre servicios se limita principalmente a Empleados -> Departamentos; el resto del ciclo de vida se propaga mediante RabbitMQ y el exchange `rrhh.events`.

## Puesta en marcha

1. Copie `.env.example` como `.env` y ajuste las credenciales si es necesario.
2. Levante el sistema:

```powershell
docker compose up -d --build
```

3. Compruebe el estado:

```powershell
docker compose ps -a
```

4. El Gateway queda en `http://localhost:8080` y RabbitMQ Management en `http://localhost:15672`.

Para las pruebas E2E del Reto 6 use temporalmente `ENABLE_DEV_ENDPOINTS=true`; en uso normal manténgalo en `false`.

## Pruebas automatizadas — Reto 6

| Suite | Comando | Resultados | Cobertura |
|---|---|---|---|
| Empleados unitarias + integración | `cd microservicios/gestion-empleados` + `.\mvnw.cmd verify` | `target/surefire-reports/*.xml` | `target/site/jacoco/jacoco.xml` |
| Vacaciones unitarias | `cd microservicios/vacaciones-service` + `.\run-tests.ps1` | `reports/results.trx` | `reports/coverage.xml` |
| BDD / E2E | `cd e2e-tests` + `npm ci` + `npm test` | `reports/junit.xml`, `reports/cucumber.json`, `reports/cucumber.html` | — |

Los reportes se generan localmente y no se versionan; serán consumidos por el pipeline del reto siguiente.

## Documentación histórica

Las secciones siguientes conservan decisiones y evidencias de los retos anteriores. Algunas URLs directas de microservicios pertenecen al momento histórico del reto correspondiente; en el estado actual toda API de negocio se consume a través del Gateway.

## Tecnologías

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

La solución del Reto 2 puede levantarse desde la raíz del proyecto mediante un único comando:

```bash
docker compose up --build
```

Para detener el sistema conservando los volúmenes:

```bash
docker compose down
```

Para detener el sistema y eliminar también los volúmenes:

```bash
docker compose down -v
```

La configuración de las bases de datos se encuentra preparada mediante variables de entorno. El archivo `.env` contiene las credenciales y no debe versionarse en Git.

### Verificación de arranque ordenado

El proyecto utiliza `healthcheck` en los servicios de base de datos y en el microservicio de gestión de departamentos. Además, `gestion-empleados` utiliza `depends_on` con la condición `service_healthy`, garantizando que sus dependencias estén disponibles antes de iniciar.

Para levantar el proyecto desde cero se ejecuta:

```bash
docker compose up --build
```

Luego se puede verificar el estado de los contenedores mediante:

```bash
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

```bash
docker compose down
docker compose up -d
```

y deben eliminarse cuando se utiliza:

```bash
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

---

## Reto 5 – Seguridad y Control de Acceso con JWT

Implementa autenticación mediante JWT y protege todos los endpoints con control de acceso RBAC (basado en roles) y ABAC (basado en atributos).

### Componentes Nuevos

**Auth-Service (.NET 8)** — Puerto 8086
- `POST /auth/login` - Retorna JWT de acceso
- `POST /auth/recover-password` - Inicia recuperación de contraseña
- `POST /auth/reset-password` - Establece/cambia contraseña con token
- `POST /auth/change-password` - Cambio de contraseña (requiere JWT)
- Consume: `empleado.creado`, `empleado.retirado`, `vacaciones.iniciadas`, `vacaciones.finalizadas`
- Publica: `usuario.creado`, `cuenta.activada`, `cuenta.desactivada`, `usuario.recuperacion`

**API Gateway** — Actualizado con JwtAuthenticationFilter
- Valida JWT con HMAC-SHA256
- Aplica reglas RBAC: rol `ADMIN` (acceso total), rol `USER` (solo lectura)
- Aplica reglas ABAC: USER puede modificar solo su propio perfil (`PUT /perfiles/{id}` donde `id == token.sub`)
- Propaga identidad a microservicios: `X-User-Id`, `X-User-Role`

**Vacaciones-Service** — Actualizado con Scheduler
- `VacationSchedulerWorker.cs` ejecuta cada 60 segundos (configurable)
- Detecta períodos que inician y publica `vacaciones.iniciadas`
- Detecta períodos que finalizan y publica `vacaciones.finalizadas`
- Endpoints de desarrollo (solo con `ENABLE_DEV_ENDPOINTS=true`):
  - `POST /vacaciones/{id}/forzar-inicio`
  - `POST /vacaciones/{id}/forzar-fin`

**Notificaciones-Service** — Actualizado
- Consume eventos de seguridad: `usuario.creado`, `usuario.recuperacion`, `cuenta.activada`, `cuenta.desactivada`
- Simula envío de correos con tokens de activación/recuperación

### Estados de Cuenta

| Estado | Login | Transiciones |
|--------|-------|--------------|
| `INACTIVA` | ❌ | → ACTIVA (reset-password) / → DESACTIVADA_PERMANENTE (retiro) |
| `ACTIVA` | ✅ | → SUSPENDIDA_TEMPORAL (vacaciones) / → DESACTIVADA_PERMANENTE (retiro) |
| `SUSPENDIDA_TEMPORAL` | ❌ | → ACTIVA (fin vacaciones) / → DESACTIVADA_PERMANENTE (retiro) |
| `DESACTIVADA_PERMANENTE` | ❌ | Estado terminal (no reversible) |

**Caso borde:** Si un empleado es retirado durante vacaciones, al finalizar el período NO se reactiva la cuenta (queda en `DESACTIVADA_PERMANENTE`).

### Tokens JWT

**Access Token** (60 min):
```json
{
  "sub": "E001",
  "role": "ADMIN",
  "email": "juan@empresa.com",
  "iss": "auth-service",
  "exp": 1790806609
}
```

**Reset Token** (60 min activación / 15 min recuperación):
```json
{
  "sub": "E001",
  "type": "RESET_PASSWORD",
  "pv": "E3B0C44298FC1C14",
  "iss": "auth-service",
  "exp": 1790806655
}
```

### Configuración (.env)

```bash
# JWT (clave simétrica HMAC-SHA256 - académico)
JWT_SECRET=clave-academica-reto5-cambiar-en-produccion-0123456789
JWT_ISSUER=auth-service
ACCESS_TOKEN_MINUTES=60
ACTIVATION_TOKEN_MINUTES=60
RECOVERY_TOKEN_MINUTES=15

# Administrador semilla (se crea automáticamente)
ADMIN_EMPLEADO_ID=admin
ADMIN_EMAIL=admin@empresa.com
ADMIN_PASSWORD=Admin12345

# Base de datos auth
AUTH_DB=auth
AUTH_DB_USER=auth
AUTH_DB_PASSWORD=auth
```

### Flujo de Pruebas

```bash
# 1. Login admin
curl -X POST http://localhost:8080/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@empresa.com","password":"Admin12345"}'

# 2. Usar token (guardar en variable TOKEN_ADMIN)
curl http://localhost:8080/empleados \
  -H "Authorization: Bearer $TOKEN_ADMIN"

# 3. Crear departamento y empleado (requiere ADMIN)
curl -X POST http://localhost:8080/departamentos \
  -H "Authorization: Bearer $TOKEN_ADMIN" \
  -H 'Content-Type: application/json' \
  -d '{"id":"IT","nombre":"Tecnología","descripcion":"Desarrollo"}'

curl -X POST http://localhost:8080/empleados \
  -H "Authorization: Bearer $TOKEN_ADMIN" \
  -H 'Content-Type: application/json' \
  -d '{
    "id":"E001",
    "nombre":"Juan",
    "apellido":"Perez",
    "email":"juan@empresa.com",
    "numeroEmpleado":"EMP-001",
    "cargo":"Dev",
    "area":"Backend",
    "departamentoId":"IT",
    "fechaIngreso":"2026-09-30",
    "estado":"ACTIVO"
  }'

# 4. Verificar eventos en logs
docker logs notificaciones-service --tail 20 | grep "SEGURIDAD"
# Extraer el token de activación del log

# 5. Activar cuenta del empleado
curl -X POST http://localhost:8080/auth/reset-password \
  -H 'Content-Type: application/json' \
  -d '{"token":"TOKEN_DEL_LOG","newPassword":"Juan12345"}'

# 6. Login como empleado
curl -X POST http://localhost:8080/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"juan@empresa.com","password":"Juan12345"}'

# 7. Probar RBAC (USER puede leer pero no escribir)
curl http://localhost:8080/empleados -H "Authorization: Bearer $TOKEN_USER"  # OK
curl -X DELETE http://localhost:8080/empleados/E001 -H "Authorization: Bearer $TOKEN_USER"  # 403

# 8. Probar ABAC (USER puede modificar solo su propio perfil)
curl -X PUT http://localhost:8080/perfiles/E001 \
  -H "Authorization: Bearer $TOKEN_USER" \
  -H 'Content-Type: application/json' \
  -d '{"telefono":"555-1234","ciudad":"Bogotá"}'  # OK

curl -X PUT http://localhost:8080/perfiles/E002 \
  -H "Authorization: Bearer $TOKEN_USER" \
  -H 'Content-Type: application/json' \
  -d '{"telefono":"555-9999"}'  # 403
```

### Limitaciones Conocidas

**Scheduler de Vacaciones:**
- Funciona correctamente con 1 instancia del `vacaciones-service`
- Con N instancias, cada una ejecuta su propio scheduler (N eventos duplicados)
- Deduplicación en consumidores mitiga pero no elimina trabajo redundante
- Solución: Lock distribuido (ShedLock) para Reto 31

**Seguridad JWT:**
- Usa clave simétrica HMAC-SHA256 (académico)
- Producción requeriría RS256/ES256 o JWKS con rotación de claves
- `JWT_SECRET` debe estar en gestor de secretos, no en `.env`
