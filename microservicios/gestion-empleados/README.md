# Reto 1 – Servidor Web para Gestión Básica de Empleados

## ¿Qué es este proyecto?

Este microservicio, llamado `gestion-empleados`, es el primer componente de un sistema más grande que gestionará todo el ciclo de vida de los empleados de una empresa (desde que ingresan hasta que se retiran), construido con una arquitectura de microservicios.

En este primer reto, el objetivo es simple: construir un servidor web que permita **registrar empleados** y **consultarlos por su id**, manejando correctamente las rutas, los métodos HTTP y los códigos de estado. No hay base de datos todavía — los datos se guardan en la memoria de la aplicación mientras esta se está ejecutando (si se reinicia el servidor, los datos se pierden).

## Tecnologías utilizadas

| Tecnología | Uso en el proyecto |
|---|---|
| **Java 21** | Lenguaje de programación del servicio |
| **Spring Boot 4.1.0** | Framework que provee el servidor web y el enrutamiento HTTP |
| **Maven** | Gestor de dependencias y herramienta de compilación |
| **Docker** | Empaquetado y ejecución del servicio en un contenedor, independiente del sistema operativo |

## Estructura del proyecto

El código sigue una organización por capas, algo típico en aplicaciones Spring Boot, donde cada carpeta tiene una responsabilidad clara:

```
gestion-empleados/
├── src/main/java/co/edu/uniquindio/gestionempleados/
│   ├── GestionEmpleadosApplication.java   # Punto de entrada de la aplicación
│   ├── controller/
│   │   └── EmpleadoController.java        # Recibe las peticiones HTTP y las delega al servicio
│   ├── service/
│   │   └── EmpleadoService.java           # Contiene la lógica de negocio y las validaciones
│   ├── repository/
│   │   └── EmpleadoRepository.java        # Guarda y consulta los empleados en memoria
│   ├── model/
│   │   ├── Empleado.java                  # Estructura de datos de un empleado
│   │   └── EstadoEmpleado.java            # Los posibles estados de un empleado
│   └── exception/
│       ├── EmpleadoDuplicadoException.java     # Se lanza si el email o número de empleado ya existen
│       ├── EmpleadoNoEncontradoException.java  # Se lanza si se consulta un id que no existe
│       └── GlobalExceptionHandler.java         # Traduce cada excepción a una respuesta HTTP
├── Dockerfile                              # Instrucciones para construir la imagen del contenedor
└── pom.xml                                 # Configuración de Maven y dependencias
```

**¿Por qué esta separación en capas?** Cada clase tiene una única responsabilidad: el `Controller` solo se encarga de recibir y responder peticiones HTTP, el `Service` contiene las reglas de negocio (por ejemplo, "no permitir emails duplicados"), y el `Repository` es el único lugar que sabe *cómo* se almacenan los datos. Esto facilita que, en retos futuros, se pueda cambiar el almacenamiento en memoria por una base de datos real sin tener que tocar el resto del código.

## Modelo canónico de Empleado

Este es el modelo de datos que representa a un empleado en todo el sistema. Se definió completo desde este primer reto para no tener que migrarlo más adelante:

```json
{
  "id": "E001",
  "nombre": "Juan",
  "apellido": "Pérez",
  "email": "juan.perez@empresa.com",
  "numeroEmpleado": "EMP-2026-001",
  "cargo": "Desarrollador Senior",
  "area": "Tecnología",
  "departamentoId": "IT",
  "fechaIngreso": "2026-02-10",
  "estado": "ACTIVO"
}
```

### Explicación de cada campo

| Campo | Tipo | Descripción |
|---|---|---|
| `id` | texto | Identificador único del empleado |
| `nombre` | texto | Nombre del empleado |
| `apellido` | texto | Apellido del empleado |
| `email` | texto | Correo electrónico. Debe ser único en el sistema |
| `numeroEmpleado` | texto | Código interno de la empresa. Debe ser único en el sistema |
| `cargo` | texto | Puesto que ocupa el empleado |
| `area` | texto | Área o unidad de negocio a la que pertenece |
| `departamentoId` | texto | Referencia al departamento. En este reto es texto libre; el servicio de departamentos se implementará en el Reto 2 |
| `fechaIngreso` | fecha (`AAAA-MM-DD`) | Fecha en la que el empleado ingresó a la empresa |
| `estado` | enum | Estado actual del empleado (ver abajo) |

### Estados posibles del empleado

El modelo contempla tres estados, aunque en este reto solo se utiliza `ACTIVO`:

- **ACTIVO**: empleado vinculado y con acceso al sistema.
- **EN_VACACIONES**: vinculado, pero con el acceso suspendido temporalmente (se implementa en retos futuros).
- **RETIRADO**: desvinculado. Es un estado final — el registro se conserva para auditoría y nunca se elimina (también se implementa más adelante).

Si al registrar un empleado no se envía el campo `estado`, el sistema le asigna automáticamente `ACTIVO`.

## Endpoints disponibles

### 1. Registrar un empleado

```
POST /empleados
```

Esta operación recibe los datos de un empleado en el cuerpo de la petición y lo guarda en el sistema.

**Cuerpo de la petición (JSON):** un objeto con la estructura del modelo canónico descrito arriba.

**Respuesta exitosa:**
- Código: `200 OK`
- Cuerpo: el empleado registrado, tal como quedó guardado.

**Errores posibles — `400 Bad Request`:**

El servicio valida que no se dupliquen dos campos clave. Si alguna validación falla, responde con el código `400` y un mensaje describiendo el problema:

- Si el `email` ya está registrado por otro empleado:
  ```
  El email {email} ya está registrado
  ```
- Si el `numeroEmpleado` ya está registrado por otro empleado:
  ```
  El número de empleado {numeroEmpleado} ya está registrado
  ```

### 2. Consultar un empleado por id

```
GET /empleados/{id}
```

Esta operación busca y devuelve la información de un empleado a partir de su identificador (`{id}` es el valor que reemplazás en la URL, por ejemplo `/empleados/E001`).

**Respuesta exitosa:**
- Código: `200 OK`
- Cuerpo: la información completa del empleado correspondiente al `{id}` solicitado.

**Si el empleado no existe:**
- Código: `404 Not Found`
- Cuerpo:
  ```
  El empleado con id {id} no existe
  ```

### 3. Rutas o métodos no soportados

Cualquier petición a una ruta que no sea `/empleados` o `/empleados/{id}`, o que use un método HTTP distinto de los definidos (por ejemplo, un `DELETE`), responde de forma genérica:

- Código: `404 Not Found`
- Cuerpo:
  ```
  Recurso no encontrado
  ```

Este comportamiento está centralizado en la clase `GlobalExceptionHandler`, que intercepta tanto las rutas inexistentes como los métodos HTTP no soportados y responde de manera uniforme.

## Cómo ejecutar el proyecto

### Opción A: ejecución local (sin Docker)

Útil para desarrollar y depurar directamente en tu máquina.

```bash
cd microservicios/gestion-empleados
./mvnw clean package -DskipTests
java -jar target/gestion-empleados-0.0.1-SNAPSHOT.jar
```

El primer comando compila el proyecto y genera el `.jar`; el segundo lo ejecuta. La aplicación queda disponible en `http://localhost:8080`.

### Opción B: ejecución con Docker (recomendada)

El proyecto incluye un `Dockerfile` de **dos etapas** (multi-stage build):

1. **Etapa de compilación**: usa una imagen con Maven y JDK 21 para compilar el proyecto y generar el `.jar`.
2. **Etapa de ejecución**: usa una imagen mucho más liviana, que solo tiene el JRE (no Maven ni el código fuente), y copia el `.jar` ya compilado. Esto hace que la imagen final sea más pequeña y rápida de descargar/distribuir.

**Paso 1 — Construir la imagen del contenedor:**

Desde la carpeta `microservicios/gestion-empleados` (donde está el `Dockerfile`):

```bash
docker build -t servidor-empleados .
```

- `docker build`: le indica a Docker que construya una imagen siguiendo las instrucciones del `Dockerfile`.
- `-t servidor-empleados`: le asigna el nombre `servidor-empleados` a la imagen resultante.
- `.`: indica que el `Dockerfile` está en la carpeta actual.

**Paso 2 — Levantar el contenedor:**

```bash
docker run -p 8080:8080 servidor-empleados
```

- `docker run`: crea y arranca un contenedor a partir de la imagen.
- `-p 8080:8080`: conecta el puerto 8080 de tu computadora con el puerto 8080 dentro del contenedor (el que declara `EXPOSE 8080` en el Dockerfile).
- `servidor-empleados`: el nombre de la imagen construida en el paso anterior.

Una vez levantado, la aplicación queda disponible en `http://localhost:8080`, igual que en la ejecución local.

## Pruebas del servidor con Postman

Las pruebas de este servicio se realizaron con **Postman**, verificando que cada endpoint responda con el código de estado y el cuerpo esperado según lo definido en la consigna.

### Preparación

1. Abrir Postman y crear una nueva **Collection** llamada, por ejemplo, `Gestión de Empleados`.
2. (Opcional pero recomendado) Crear un **Environment** con una variable `base_url` cuyo valor sea `http://localhost:8080`. Esto permite usar `{{base_url}}/empleados` en cada request y cambiar de entorno fácilmente en el futuro (por ejemplo, si el servicio se despliega en otro host).
3. Asegurarse de que el servidor esté corriendo (localmente o en Docker) antes de enviar las peticiones.

### Caso 1 — Registrar un empleado exitosamente

- **Método:** `POST`
- **URL:** `{{base_url}}/empleados`
- **Body:** seleccionar `raw` → `JSON`, y pegar:
  ```json
  {
    "id": "E001",
    "nombre": "Juan",
    "apellido": "Pérez",
    "email": "juan.perez@empresa.com",
    "numeroEmpleado": "EMP-2026-001",
    "cargo": "Desarrollador Senior",
    "area": "Tecnología",
    "departamentoId": "IT",
    "fechaIngreso": "2026-02-10",
    "estado": "ACTIVO"
  }
  ```
- **Resultado esperado:** `200 OK`, con el mismo empleado devuelto en el cuerpo de la respuesta.

### Caso 2 — Registrar un empleado con email duplicado

- **Método:** `POST`
- **URL:** `{{base_url}}/empleados`
- **Body:** el mismo del Caso 1, pero con un `id` y `numeroEmpleado` distintos y el mismo `email`:
  ```json
  {
    "id": "E002",
    "nombre": "Ana",
    "apellido": "Gómez",
    "email": "juan.perez@empresa.com",
    "numeroEmpleado": "EMP-2026-002",
    "cargo": "QA",
    "area": "Tecnología",
    "departamentoId": "IT",
    "fechaIngreso": "2026-02-11",
    "estado": "ACTIVO"
  }
  ```
- **Resultado esperado:** `400 Bad Request`, con el mensaje `El email juan.perez@empresa.com ya está registrado`.

### Caso 3 — Registrar un empleado con número de empleado duplicado

- **Método:** `POST`
- **URL:** `{{base_url}}/empleados`
- **Body:** distinto `id` y `email`, pero mismo `numeroEmpleado` que el Caso 1.
- **Resultado esperado:** `400 Bad Request`, con el mensaje `El número de empleado EMP-2026-001 ya está registrado`.

### Caso 4 — Consultar un empleado existente

- **Método:** `GET`
- **URL:** `{{base_url}}/empleados/E001`
- **Resultado esperado:** `200 OK`, con la información completa del empleado `E001`.

### Caso 5 — Consultar un empleado que no existe

- **Método:** `GET`
- **URL:** `{{base_url}}/empleados/E999`
- **Resultado esperado:** `404 Not Found`, con el mensaje `El empleado con id E999 no existe`.

### Caso 6 — Ruta no soportada

- **Método:** `GET`
- **URL:** `{{base_url}}/ruta-que-no-existe`
- **Resultado esperado:** `404 Not Found`, con el mensaje `Recurso no encontrado`.

### Caso 7 — Método HTTP no soportado

- **Método:** `DELETE`
- **URL:** `{{base_url}}/empleados/E001`
- **Resultado esperado:** `404 Not Found`, con el mensaje `Recurso no encontrado` (en este reto no existe un endpoint para eliminar empleados).

## Notas adicionales

- No se usa base de datos en este reto: los empleados se guardan en memoria mediante un `ConcurrentHashMap`, por lo que los datos se pierden al reiniciar el servicio.
- El puerto expuesto por defecto es `8080`, tanto en ejecución local como en Docker.
- Este servicio será extendido en retos posteriores, incorporando el resto de los microservicios (departamentos, autenticación, perfiles, vacaciones, notificaciones y el API Gateway) y una base de datos persistente.