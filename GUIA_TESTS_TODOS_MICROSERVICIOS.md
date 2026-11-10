# Pruebas y cobertura de todos los microservicios

Esta ampliación agrega pruebas automatizadas a los servicios que todavía no tenían una suite propia, sin modificar la lógica funcional de producción. La suite BDD/Cucumber sigue siendo única y global porque valida el ecosistema completo de extremo a extremo; no se duplica dentro de cada microservicio.

## Resumen

| Microservicio | Stack de pruebas | Comando | Reporte de cobertura |
|---|---|---|---|
| api-gateway | JUnit 5 + Reactor Test | `mvn verify` o `./run-tests.ps1` | `target/site/jacoco/jacoco.xml` + HTML en `target/site/jacoco/index.html` |
| gestion-empleados | JUnit 5 + Mockito + Testcontainers | `./mvnw.cmd verify` | `target/site/jacoco/jacoco.xml` |
| gestion-departamentos | pytest + pytest-cov | `./run-tests.ps1` | `coverage.xml` |
| notificaciones-service | Node.js test runner + V8 coverage | `npm run test:ci` | `coverage/lcov.info`, `coverage/summary.json`, `reports/junit.xml` |
| perfiles-service | Go testing | `./run-tests.ps1` | `coverage.out`, `reports/coverage.txt` |
| vacaciones-service | xUnit + Coverlet | `./run-tests.ps1` | `reports/coverage.xml`, `reports/results.trx` |
| auth-service | xUnit + Coverlet | `./run-tests.ps1` | `reports/coverage.xml`, `reports/results.trx` |
| E2E global | Cucumber.js + Gherkin | `npm test` en `e2e-tests` | `reports/junit.xml`, `reports/cucumber.json`, `reports/cucumber.html` |

## Qué se agregó

### API Gateway
- Validación de rutas públicas.
- 401 sin JWT, JWT inválido, expirado, issuer incorrecto y reset token usado como access token.
- RBAC ADMIN/USER y ABAC de perfil propio.
- Propagación `X-User-Id` / `X-User-Role`.
- Manejo global de 404, 401, 403, 500 y 503.
- Smoke unitario del filtro de trazabilidad.
- JaCoCo para generar XML y HTML de cobertura.

### Auth Service
- Política de contraseñas y BCrypt.
- Tokens de acceso y reset.
- Validación de reset token y rechazo de access token como reset.
- Fingerprint de contraseña.
- DevTokenStore case-insensitive.
- Coverlet en formato Cobertura.

### Gestión de Departamentos
- Se conservaron los tests API existentes.
- Se agregaron validaciones de schemas.
- Se agregaron pruebas de `/health`, `/metrics` y BearerAuth en OpenAPI.
- Scripts locales que generan JUnit XML y `coverage.xml`.

### Notificaciones
- Procesamiento de eventos de empleados, vacaciones, seguridad y cuentas.
- Contratos inválidos y deduplicación.
- Persistencia con transacciones simuladas.
- Controladores HTTP.
- Consumidor RabbitMQ: ack, nack, reintento y tipos no gestionados.
- Swagger/OpenAPI.
- Cobertura V8 transformada a LCOV sin agregar una librería de testing adicional.

### Perfiles
- Carga de configuración y variables de entorno.
- Compatibilidad `empleadoId` / `id` legado.
- UUID interno.
- Swagger/OpenAPI, `/metrics`, 404 y validaciones del PUT antes de tocar BD.
- `coverage.out` nativo de Go.

## Cucumber / BDD

No debe haber un Cucumber por microservicio. Los tests unitarios y de componente cubren cada servicio de manera aislada. Cucumber prueba los flujos completos del sistema a través del API Gateway: smoke, seguridad, onboarding, vacaciones y offboarding.
