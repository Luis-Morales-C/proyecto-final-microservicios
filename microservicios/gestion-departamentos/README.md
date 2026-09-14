# Gestión de Departamentos - Reto 2

## Descripción

`gestion-departamentos` es el segundo microservicio de negocio incorporado en el Reto 2.

Su propósito es administrar los departamentos de forma independiente y proporcionar mediante HTTP REST la información que necesita `gestion-empleados` para validar el `departamentoId` de un empleado.

El servicio tiene su propio contenedor, su propio Dockerfile, su propia configuración y su propia base de datos PostgreSQL.

## Objetivo del Reto 2

Implementar un segundo servicio de negocio utilizando un lenguaje diferente al servicio de empleados, persistir sus datos en una base de datos independiente y exponer endpoints REST documentados mediante OpenAPI/Swagger.

## Tecnologías utilizadas

| Tecnología | Uso |
|---|---|
| Python 3.11 | Lenguaje de programación |
| FastAPI | Framework de API REST |
| Uvicorn | Servidor ASGI |
| Pydantic | Validación de datos |
| SQLAlchemy | ORM y persistencia |
| psycopg2-binary | Driver PostgreSQL |
| PostgreSQL 18 | Base de datos |
| Pytest | Pruebas automatizadas |
| HTTPX | Cliente utilizado en pruebas |
| Docker | Contenerización |

## Estructura

```text
gestion-departamentos/
├── app/
│   ├── main.py
│   ├── database.py
│   ├── models.py
│   ├── schemas.py
│   ├── crud.py
│   └── exceptions.py
├── test/
│   ├── conftest.py
│   ├── test_api.py
│   └── schemas.py
├── Dockerfile
└── requirements.txt
```

## Modelo de Departamento

```json
{
  "id": "IT",
  "nombre": "Tecnologia",
  "descripcion": "Departamento de tecnologia"
}
```

El identificador `id` es la clave primaria del departamento.

Las validaciones de entrada se encuentran definidas en `schemas.py`.

## Endpoints


### POST `/departamentos`

Registra un departamento.

Resultados:

```text
201 Created → registro exitoso
400 Bad Request → identificador duplicado
422 Unprocessable Entity → datos inválidos
503 Service Unavailable → base de datos no disponible
```

### GET `/departamentos/{departamento_id}`

Consulta un departamento por identificador.

Resultados:

```text
200 OK → departamento encontrado
404 Not Found → departamento inexistente
503 Service Unavailable → base de datos no disponible
```

### GET `/departamentos`

Lista todos los departamentos.

Resultado:

```text
200 OK
```

## Persistencia

El servicio utiliza una base de datos PostgreSQL independiente:

```text
database-departamentos
```

El puerto publicado al host es:

```text
5434
```

El puerto interno utilizado por la aplicación dentro de Docker es:

```text
5432
```

El volumen correspondiente es:

```text
departamentos-db-data
```

## Creación del esquema


Para el Reto 2 se eligió auto-DDL de SQLAlchemy.

El esquema se crea mediante:

```text
Base.metadata.create_all(bind=engine)
```

De esta forma, al iniciar el servicio con una base de datos nueva, SQLAlchemy crea las tablas necesarias automáticamente.

La ventaja para este reto es que no requiere pasos manuales adicionales.

Como limitación, esta estrategia no proporciona un historial versionado de migraciones. Para una evolución posterior del proyecto se podría utilizar Alembic.

## Configuración


La conexión a PostgreSQL utiliza:

```text
DB_HOST
DB_PORT
DB_NAME
DB_USER
DB_PASSWORD
```

Dentro de Docker:

```text
DB_HOST=database-departamentos
DB_PORT=5432
```

Desde el host, el servicio de API está disponible en:

```text
http://localhost:8000
```

Dentro de la red Docker otros servicios deben utilizar:

```text
http://gestion-departamentos:8000
```

## Docker


El servicio posee su propio Dockerfile basado en:

```text
python:3.11-slim
```

El contenedor instala las dependencias, copia la aplicación y ejecuta Uvicorn.

El despliegue completo se realiza desde la raíz mediante:

```text
docker compose up --build
```

## Health check

El servicio de departamentos dispone de un `healthcheck` en Docker Compose que consulta:

```text
GET /departamentos
```

Esto permite que Docker determine cuándo la aplicación está lista para atender solicitudes.

`gestion-empleados` depende de este servicio mediante:

```text
condition: service_healthy
```

## Comunicación con Gestión de Empleados



`gestion-empleados` no accede directamente a esta base de datos.

Cuando necesita comprobar un departamento utiliza:

```text
GET http://gestion-departamentos:8000/departamentos/{id}
```

La propiedad de los datos se mantiene dentro de cada microservicio.

**Evidencia:**

```text
docs/evidencias/reto2/integracion/R2-18-comunicacion-rest.png
```

## Swagger / OpenAPI


FastAPI genera automáticamente la documentación OpenAPI.

Swagger UI:

```text
http://localhost:8000/docs
```

ReDoc:

```text
http://localhost:8000/redoc
```

OpenAPI:

```text
http://localhost:8000/openapi.json
```

**Evidencia:**

```text
docs/evidencias/reto2/swagger/R2-21-swagger-departamentos.png
```

## Pruebas del servicio


Las pruebas automatizadas del servicio utilizan una base de datos SQLite aislada para los casos de prueba.

La ejecución local es:

```text
pytest -q
```

Los casos contemplan creación, duplicados, consultas, inexistentes, listado, validaciones de entrada, rutas no existentes, métodos no soportados y disponibilidad de Swagger/OpenAPI.

Las pruebas de integración contra el entorno Docker/PostgreSQL deben demostrarse mediante las evidencias manuales del Reto 2.

## Evidencias del Reto 2


```text
docs/evidencias/reto2/
```

### R2-01 - Crear departamento

```text
POST http://localhost:8000/departamentos
```

Resultado esperado:

```text
201 Created
```

**Imagen:**

```text
docs/evidencias/reto2/postman/R2-07-crear-departamento.png
```

### R2-02 - Consultar departamento

```text
GET http://localhost:8000/departamentos/IT
```

Resultado esperado:

```text
200 OK
```

**Imagen:**

```text
docs/evidencias/reto2/postman/R2-08-get-departamento.png
```

### R2-03 - Listar departamentos

```text
GET http://localhost:8000/departamentos
```

Resultado esperado:

```text
200 OK
```

**Imagen:**

```text
docs/evidencias/reto2/postman/R2-09-listar-departamentos.png
```

### R2-04 - Departamento duplicado

Resultado esperado:

```text
400 Bad Request
```

**Imagen:**

```text
docs/evidencias/reto2/postman/R2-10-departamento-duplicado.png
```

### R2-05 - Departamento inexistente

Resultado esperado:

```text
404 Not Found
```

**Imagen:**

```text
docs/evidencias/reto2/postman/R2-05-departamento-no-existe.png
```

### R2-06 - Swagger departamentos

**Imagen:**

```text
docs/evidencias/reto2/swagger/R2-21-swagger-departamentos.png
```

### R2-07 - Arranque ordenado

La captura debe mostrar la salida de:

```text
docker compose ps
```

con las bases de datos y el servicio de departamentos en estado `healthy`.

**Imagen:**

```text
docs/evidencias/reto2/docker/R2-02-compose-ps.png
```

### R2-08 - Persistencia

La prueba debe mostrar que los departamentos continúan disponibles después de:

```text
docker compose down
docker compose up -d
```

**Imagen:**

```text
docs/evidencias/reto2/docker/R2-05-persistencia-down.png
```

### R2-09 - Eliminación de volúmenes

La prueba debe mostrar la pérdida de los datos después de:

```text
docker compose down -v
docker compose up -d
```

**Imagen:**

```text
docs/evidencias/reto2/docker/R2-06-persistencia-down-v.png
```

## Estado final del servicio


Al finalizar el Reto 2, `gestion-departamentos` cuenta con:

- Python 3.11 + FastAPI.
- PostgreSQL independiente.
- SQLAlchemy.
- Auto-DDL.
- Dockerfile propio.
- Volumen propio.
- Variables de entorno.
- Health check.
- Endpoints POST y GET.
- Validaciones de entrada.
- Manejo de errores.
- Swagger/OpenAPI.
- Integración REST con `gestion-empleados`.

Las funcionalidades reservadas para retos posteriores no se presentan como implementadas en este servicio.
