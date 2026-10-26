# Solución: Fallo Intermitente en Activación de Empleados

## 🐛 Problema Detectado

### Síntoma
Fallo intermitente (flaky) en escenarios de la feature "Gestión y ciclo de vida de vacaciones" con el error:

```
No se pudo activar <email>: {"title":"Token inválido","status":400,"detail":"El token es inválido, expiró o ya fue utilizado."} 400 !== 200
```

### Contexto
- Ocurre en el paso: `Y que existe un empleado activo con credenciales establecidas`
- Falla en la función `activarEmpleado` (~línea 129 de fixtures.js)
- El fallo ocurre ~0.5 segundos después de crear el empleado
- El token no puede haber expirado (duración: 60 minutos)
- Otros escenarios de la misma ejecución pasan correctamente

### Causa Raíz (Hipótesis)
**Race condition** entre:
1. La creación asíncrona de la cuenta en `auth-service` (evento de RabbitMQ)
2. La validación del token en el POST `/auth/reset-password`

El endpoint de test `/auth/test/reset-token` devuelve un token que el POST aún no puede aceptar porque la cuenta no está completamente inicializada en la base de datos.

**Evidencia en logs**:
- Cuenta creada (INACTIVA) ✅
- Contraseña establecida ❌ (nunca ocurre)

## ✅ Solución Implementada

### Cambios en `fixtures.js`

La función `activarEmpleado` ahora:

1. **Reintenta automáticamente** cuando recibe `400` con "Token inválido"
2. **Obtiene un nuevo token** en cada reintento
3. **Falla inmediatamente** para cualquier otro error
4. **Mejora el diagnóstico** incluyendo token y respuesta en el mensaje de error

### Código Anterior (Vulnerable a Race Condition)

```javascript
export async function activarEmpleado(world, email, password = "ClaveBDD123") {
    const tokenInfo = await esperarTokenActivacion(world, email);

    const { response, body } = await requestJson(world.baseUrl, "/auth/reset-password", {
        method: "POST",
        body: {
            token: tokenInfo.token,
            newPassword: password
        }
    });

    assert.equal(response.status, 200,
        `No se pudo activar ${email}: ${JSON.stringify(body)}`);

    world.password = password;
    return password;
}
```

### Código Nuevo (Resiliente)

```javascript
export async function activarEmpleado(world, email, password = "ClaveBDD123") {
    let ultimoToken = "";

    const result = await esperarHastaQue(async () => {
        const tokenInfo = await esperarTokenActivacion(world, email);
        ultimoToken = tokenInfo.token ?? "";

        const intento = await requestJson(world.baseUrl, "/auth/reset-password", {
            method: "POST",
            body: {
                token: tokenInfo.token,
                newPassword: password
            }
        });

        if (intento.response.status === 400 &&
            JSON.stringify(intento.body).includes("Token inválido")) {
            return false;  // Reintenta con un nuevo token
        }
        return intento;  // Éxito o error diferente
    }, { descripcion: `la activación de ${email}` });

    assert.equal(result.response.status, 200,
        `No se pudo activar ${email}. Token: ${ultimoToken.slice(0, 40)}... ` +
        `Respuesta: ${JSON.stringify(result.body)}`);

    world.password = password;
    return password;
}
```

## 🎯 Beneficios

### 1. Resiliencia ante Race Conditions
- **Antes**: Fallaba inmediatamente si el token no era aceptado
- **Ahora**: Reintenta hasta 15 veces (15 segundos) esperando que la cuenta se sincronice

### 2. Patrón Consistente
Usa el mismo helper `esperarHastaQue` que ya usan otras funciones:
- `esperarTokenActivacion`
- `esperarPerfil`
- `esperarNotificacion`
- `programarVacacion`

### 3. Mejor Diagnóstico
Si después de los reintentos sigue fallando, el mensaje incluye:
- Primeros 40 caracteres del token usado
- Cuerpo completo de la respuesta
- Contexto del email que falló

**Ejemplo de mensaje mejorado**:
```
No se pudo activar empleado@empresa.com. Token: eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9... Respuesta: {"title":"Token inválido","status":400,"detail":"El token es inválido, expiró o ya fue utilizado."}
```

### 4. Selectivo en Errores
- **Reintenta**: Solo cuando detecta `400` + "Token inválido"
- **Falla inmediatamente**: Para cualquier otro error (401, 403, 500, etc.)

## 📋 Criterios de Aceptación

✅ Implementado:
1. La función usa `esperarHastaQue` para reintentos automáticos
2. Detecta específicamente el error "Token inválido" con código 400
3. Obtiene un nuevo token en cada reintento
4. El mensaje de error incluye token (40 chars) y respuesta completa

🧪 Por Validar:
1. Correr la suite 3 veces seguidas sin fallos por "Token inválido"
2. Verificar que el mensaje mejorado aparece si falla después de reintentos

## 🔧 Configuración de Reintentos

Los reintentos usan la configuración global de `esperarHastaQue`:

```javascript
// Valores por defecto
maxIntentos = 15          // Puede sobreescribirse con POLL_MAX_ATTEMPTS
intervaloMs = 1000        // Puede sobreescribirse con POLL_INTERVAL_MS
```

**Tiempo máximo de espera**: 15 segundos (15 intentos × 1 segundo)

## 🔍 Validación

### Comando para ejecutar la suite
```bash
cd e2e-tests
npm ci
npx cucumber-js
```

### Qué observar
1. Los escenarios de vacaciones deben pasar consistentemente
2. En caso de fallo, verificar que el mensaje incluye token y respuesta
3. Revisar los logs de auth-service para confirmar la sincronización

## 📝 Próximos Pasos (Si el Problema Persiste)

Si después de esta solución el problema continúa, la causa está en `auth-service`:

### Áreas a Investigar

1. **Endpoint `/auth/test/reset-token`**
   - ¿Devuelve token antes de que la cuenta esté lista?
   - ¿Debería verificar el estado de la cuenta antes de generar el token?

2. **Endpoint `/auth/reset-password`**
   - ¿Cómo valida el token?
   - ¿Verifica que la cuenta existe y está en estado INACTIVA?

3. **Claim `pv` del JWT**
   - Valor actual: `E3B0C44298FC1C14` (SHA-256 de cadena vacía)
   - ¿Es correcto o indica un problema de inicialización?

4. **Handler de Eventos RabbitMQ**
   - ¿Cuánto tarda en procesar `empleado.creado`?
   - ¿Hay alguna condición que pueda hacer que falle silenciosamente?

### Solución Potencial en auth-service
```csharp
// En GET /auth/test/reset-token
var cuenta = await _context.Cuentas
    .FirstOrDefaultAsync(c => c.Email == email);

if (cuenta == null)
    return NotFound();

// AGREGAR: verificar que la cuenta está lista para ser activada
if (cuenta.Estado != EstadoCuenta.Inactiva)
    return BadRequest("La cuenta no está en estado INACTIVA");

// Generar token solo si la cuenta está completamente inicializada
var token = GenerarTokenRecuperacion(cuenta);
```

## 📚 Referencias

- **Patrón de Polling**: `e2e-tests/support/polling.js`
- **Helper HTTP**: `e2e-tests/support/http.js`
- **Otros Fixtures**: Funciones como `programarVacacion` que ya usan este patrón

## ✨ Lecciones Aprendidas

1. **Sistemas distribuidos son eventualmente consistentes**: Los eventos de RabbitMQ no son instantáneos
2. **Los tests E2E deben ser resilientes**: Usar polling en lugar de asserts directos
3. **Mensajes de error informativos**: Facilitan el diagnóstico de problemas intermitentes
4. **Patrones consistentes**: Reutilizar helpers probados (`esperarHastaQue`)
