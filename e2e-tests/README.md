# e2e-tests — Reto 6

Suite funcional de extremo a extremo con **Cucumber.js 13.3.0** y Gherkin en español.

## Qué cubre

- Smoke test del API Gateway.
- Seguridad: 401 sin token, 401 con token inválido, lectura USER, escritura denegada, propiedad del perfil y permisos ADMIN.
- Onboarding: registro, creación asíncrona de cuenta, notificación, activación y login.
- Vacaciones: programación, fechas inválidas, solapamiento, suspensión y reactivación de cuenta.
- Offboarding: retiro lógico, notificación, bloqueo de login, recuperación bloqueada, auditoría y retiro durante vacaciones.

## Prerrequisitos

- Sistema completo levantado con Docker Compose.
- Node.js 22 y npm.
- Archivo `.env` en la raíz del repositorio basado en `.env.example`.
- Para los escenarios que necesitan acelerar el tiempo: `ENABLE_DEV_ENDPOINTS=true`.

Los endpoints de desarrollo están deshabilitados por defecto, solo existen cuando esa variable vale `true` y las operaciones sensibles siguen requiriendo un JWT `ADMIN`.

## Instalación

Desde `e2e-tests/`:

```powershell
npm ci
```

## Ejecución

Suite completa:

```powershell
npm test
```

Suites individuales:

```powershell
npm run test:smoke
npm run test:security
npm run test:onboarding
npm run test:vacaciones
npm run test:offboarding
```

## Polling

Los resultados asíncronos se verifican mediante polling; no se usa un `sleep` fijo como única estrategia.

Variables opcionales:

```env
POLL_MAX_ATTEMPTS=15
POLL_INTERVAL_MS=1000
```

Con esos valores el timeout máximo típico es de unos 15 segundos, pero la prueba termina antes si la condición se cumple rápidamente.

## Reportes

Cada ejecución de la suite completa genera:

```text
e2e-tests/reports/
├── junit.xml
├── cucumber.json
└── cucumber.html
```

La carpeta se recrea en cada ejecución y está ignorada por Git.

## Por qué existe `/auth/test/reset-token`

El Reto 5 evita guardar o exponer el token de activación en `GET /notificaciones`, lo cual es correcto desde seguridad. Para automatizar el onboarding del Reto 6 sin analizar logs, `auth-service` ofrece un endpoint **exclusivo de desarrollo/E2E**, habilitado con `ENABLE_DEV_ENDPOINTS=true` y protegido con rol `ADMIN`. En ejecución normal el endpoint ni siquiera se registra.
