## Circuit Breaker (Reto 3 – Parte 2)

Protege la llamada síncrona **empleados-service → departamentos-service**. Cuando departamentos está caído, empleados deja de intentar la llamada, responde de inmediato con un fallback y sigue atendiendo peticiones, en lugar de acumular hilos bloqueados esperando timeouts (fallo en cascada).

El Circuit Breaker vive en **quien llama** (empleados), no en quien recibe. No hay lógica de negocio adicional en el Gateway.

### Tecnología elegida

| Aspecto | Decisión |
|---|---|
| Librería | **Resilience4j** (`resilience4j-circuitbreaker` 2.4.0) |
| Integración | Uso programático del módulo base, sin starter de Spring |
| Por qué | Es la librería estándar del ecosistema Java para este patrón (no se implementó a mano) y expone métricas del estado del circuito. Se usa el módulo base porque no depende de la versión de Spring Boot: el proyecto corre en Spring Boot 4.1 y el starter oficial está publicado para Boot 3. |

### Dónde está en el código

| Archivo | Responsabilidad |
|---|---|
| `config/ResilienciaConfig.java` | Define el circuito `departamentos` (parámetros) y registra en el log cada cambio de estado |
| `client/DepartamentoClient.java` | Ejecuta la consulta dentro del circuito. Si está abierto, lanza `DepartamentoNoDisponibleException` sin tocar la red |
| `service/EmpleadoService.java` | Implementa el **fallback**: captura `DepartamentoNoDisponibleException` y registra al empleado como `PENDIENTE_VALIDACION`. También contiene la reconciliación |
| `controller/CircuitBreakerController.java` | Expone el estado del circuito (`GET /empleados/circuit-breaker`) |
| `controller/EmpleadoController.java` | Expone la reconciliación (`POST /empleados/reconciliar`) |

Las rutas internas existentes no cambiaron (compatibilidad con el Reto 2). Solo se agregaron los dos endpoints anteriores.

### Estados

| Estado | Comportamiento |
|---|---|
| `CLOSED` | Las llamadas a departamentos se hacen normalmente y se cuentan los fallos |
| `OPEN` | Las llamadas se bloquean: respuesta inmediata del fallback, sin usar la red |
| `HALF_OPEN` | Se permite **una** llamada de prueba. Si funciona, el circuito cierra; si falla, vuelve a abrir |

La transición `OPEN → HALF_OPEN` es automática al cumplirse el tiempo de espera. Nadie tiene que reiniciar nada cuando departamentos vuelve.

### Parámetros del Circuit Breaker y justificación

| Parámetro | Valor                                                          | Justificación |
|---|----------------------------------------------------------------|---|
| Umbral de fallos | 3 fallos consecutivos (ventana de 3 llamadas, 100 % de fallos) | Cada consulta ya incluye 4 intentos internos con esperas de 1, 2 y 4 s (Reto 2), es decir, unos 7 s por petición fallida. Con 3 fallos el circuito abre en ~20 s; un umbral mayor prolongaría la degradación sin aportar más certeza |
| Tiempo en `OPEN` | 50 s                                                           | Da tiempo a que departamentos reinicie sin dejar a empleados degradado mucho rato. Es el valor bajo del rango sugerido (30–60 s) y permite reproducir la recuperación con un `sleep 35` |
| Llamadas de prueba en `HALF_OPEN` | 1                                                              | Una sola sonda basta para decidir si cerrar o reabrir y evita enviar carga a un servicio que apenas se recupera |
| Timeouts de conexión y lectura | 2 s cada uno                                                   | Configurados en el Reto 2 (`DepartamentoClient`) |
| Reintentos | 4 intentos (esperas 1 s, 2 s, 4 s)                             | Configurados en el Reto 2. **Una consulta completa (con sus 4 intentos) cuenta como un solo fallo** para el circuito |

Los valores de umbral y tiempo en `OPEN` se pueden cambiar sin recompilar en `application.properties`:

```properties
circuit-breaker.departamentos.umbral-fallos=3
circuit-breaker.departamentos.espera-abierto-segundos=50
```

**Qué cuenta como fallo.** Solo `DepartamentoNoDisponibleException` (timeout, error de conexión o 5xx tras agotar reintentos). Las respuestas 404 o 4xx de departamentos **no** cuentan como fallo: significan que el servicio está vivo. Por eso, un departamento inexistente responde 400 y además cierra el circuito cuando está en `HALF_OPEN`.

### Estado observable

- **Endpoint:** `GET http://localhost:8080/empleados/circuit-breaker` (pasa por el Gateway).

  ```json
  {
    "nombre": "departamentos",
    "estado": "CLOSED",
    "llamadasFallidasEnVentana": 0,
    "llamadasBloqueadas": 0
  }
  ```

- **Logs:** cada transición queda registrada:

  ```bash
  docker compose logs -f empleados-service | grep "CIRCUIT BREAKER"
  # CIRCUIT BREAKER 'departamentos': CLOSED -> OPEN
  # CIRCUIT BREAKER 'departamentos': OPEN -> HALF_OPEN
  # CIRCUIT BREAKER 'departamentos': HALF_OPEN -> CLOSED
  ```

### Estrategia de fallback

**Decisión: registrar al empleado con `estado: PENDIENTE_VALIDACION`** cuando departamentos no está disponible o el circuito está abierto. La respuesta es `201 Created` con el empleado en ese estado.

**Por qué (disponibilidad sobre consistencia).** El registro de un empleado es una operación de Recursos Humanos que no debe detenerse porque un servicio auxiliar esté caído. Rechazar con `503` garantizaría que nunca haya datos sin verificar, pero RRHH no podría trabajar mientras departamentos esté caído. Aceptamos ese riesgo porque:

- el dato dudoso queda **explícitamente marcado** (`PENDIENTE_VALIDACION`), no mezclado con empleados verificados;
- el estado pendiente es **reconciliable** (ver siguiente sección);
- el resto de validaciones (email y número de empleado únicos, campos obligatorios) se siguen aplicando antes del fallback.

**Alternativa descartada:** aceptar con un departamento por defecto. Inventaría datos, por lo que no se considera una opción válida.

### Reconciliación del estado pendiente

Endpoint: `POST http://localhost:8080/empleados/reconciliar`

Revalida contra departamentos a cada empleado en `PENDIENTE_VALIDACION`:

| Resultado de la consulta | Nuevo estado |
|---|---|
| El departamento existe | `ACTIVO` |
| El departamento no existe (404) | `RECHAZADO` (requiere corrección manual) |
| Departamentos sigue no disponible | Se conserva `PENDIENTE_VALIDACION` y se detiene la reconciliación |

La respuesta lista los empleados que siguen pendientes. Hoy se ejecuta bajo demanda; una mejora futura sería lanzarla automáticamente con una tarea programada cuando el circuito vuelva a `CLOSED`.

### Cómo reproducir la prueba

> Los cuerpos usan todos los campos obligatorios de `Empleado`. Con cuerpos incompletos, la validación devolvería 400 antes de llegar al circuito. Si repites la prueba, cambia los ids, emails y números de empleado: los registros del fallback quedan guardados.

```bash
# 0. Levantar todo
docker compose up -d --build

# 1. Crear un departamento con el sistema sano
curl -X POST http://localhost:8080/departamentos \
  -H "Content-Type: application/json" \
  -d '{"id":"IT","nombre":"Tecnología"}'

# 2. (Otra terminal) seguir las transiciones del circuito
docker compose logs -f empleados-service | grep "CIRCUIT BREAKER"

# 3. Detener departamentos
docker compose stop departamentos-service

# 4. Registrar 8 empleados y observar el tiempo de cada uno
for i in {1..8}; do
  echo "--- Petición $i ---"
  time curl -s -X POST http://localhost:8080/empleados \
    -H "Content-Type: application/json" \
    -d '{"id":"E00'$i'","nombre":"Test '$i'","apellido":"Prueba","email":"test'$i'@empresa.com","numeroEmpleado":"EMP-TEST-00'$i'","cargo":"QA","area":"Tecnología","departamentoId":"IT","fechaIngreso":"2026-02-10"}'
  echo
done

# 5. Estado del circuito (debe ser OPEN)
curl http://localhost:8080/empleados/circuit-breaker

# 6. Restaurar departamentos y esperar el timeout del circuito
docker compose start departamentos-service
sleep 35

# 7. Recuperación automática: un departamento inexistente debe dar 400.
#    Solo puede venir de una consulta real a departamentos.
curl -i -X POST http://localhost:8080/empleados \
  -H "Content-Type: application/json" \
  -d '{"id":"E100","nombre":"Recuperado","apellido":"Prueba","email":"recuperado@empresa.com","numeroEmpleado":"EMP-TEST-100","cargo":"QA","area":"Tecnología","departamentoId":"NO-EXISTE","fechaIngreso":"2026-02-10"}'

# 8. Estado del circuito (debe ser CLOSED)
curl http://localhost:8080/empleados/circuit-breaker

# 9. Reconciliar los empleados que quedaron pendientes
curl -X POST http://localhost:8080/empleados/reconciliar
curl http://localhost:8080/empleados
```

**Comportamiento esperado**

| Petición | Circuito | Tiempo aprox. | Respuesta |
|---|---|---|---|
| 1 a 3 | `CLOSED` → abre en la 3.ª | segundos (timeouts + reintentos, ~7 s cada una) | `201`, `PENDIENTE_VALIDACION` |
| 4 a 8 | `OPEN` | casi instantáneo (sin tocar la red) | `201`, `PENDIENTE_VALIDACION` |
| Paso 7 (tras 35 s) | `HALF_OPEN` → `CLOSED` | normal | `400` (departamento inexistente) |

### Evidencias

> Reemplazar los enlaces por las capturas reales (ajustar las rutas a donde se guarden en el repositorio).

**1. Salto en el tiempo de respuesta al abrirse el circuito**

Salida del bucle con `time`: las primeras peticiones tardan segundos y, a partir del umbral, responden casi al instante.

![Tiempos de respuesta al abrirse el circuito](docs/evidencias/cb-tiempos.png)

| Petición | Tiempo medido |
|---|---|
| 1 | _completar_ |
| 2 | _completar_ |
| 3 | _completar_ |
| 4 | _completar_ |
| 5 a 8 | _completar_ |

**2. Los tres estados en los logs**

`CLOSED -> OPEN`, `OPEN -> HALF_OPEN` y `HALF_OPEN -> CLOSED`.

![Transiciones del circuito en los logs](docs/evidencias/cb-logs-estados.png)

**3. Estado observable del circuito**

`GET /empleados/circuit-breaker` mostrando `OPEN` durante la caída y `CLOSED` tras la recuperación.

![Estado del circuito](docs/evidencias/cb-estado.png)

**4. Recuperación automática sin reiniciar nada**

Respuesta `400` (departamento inexistente) después de restaurar departamentos, sin reiniciar empleados.

![Recuperación automática](docs/evidencias/cb-recuperacion.png)

**5. Fallback y reconciliación**

Empleados en `PENDIENTE_VALIDACION` durante la caída y en `ACTIVO` tras `POST /empleados/reconciliar`.

![Reconciliación](docs/evidencias/cb-reconciliacion.png)