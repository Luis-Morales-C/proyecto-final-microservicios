# Regenerar Token de SonarQube

## Problema
Jenkins no puede autenticarse con SonarQube:
```
ERROR Not authorized. Please check the properties sonar.login and sonar.password.
```

## Solución Completa

### Paso 1: Verificar que SonarQube está corriendo
```powershell
docker ps | Select-String sonarqube
# Debe aparecer un contenedor sonarqube corriendo en puerto 9000
```

Si no está corriendo:
```powershell
docker compose -f docker-compose.ci.yml --env-file .env.infra up -d sonarqube
# Esperar 2-3 minutos para que inicie completamente
```

### Paso 2: Acceder a SonarQube
1. Abrir http://localhost:9000
2. Login con:
   - Usuario: `admin`
   - Password: `clave123` (o la que tengas en `.env.infra` como `SONAR_DB_PASSWORD`)

### Paso 3: Crear nuevo token
1. Ir a: **My Account** (ícono de usuario arriba derecha) → **Security**
2. En "Generate Tokens":
   - **Name**: `jenkins-token`
   - **Type**: **Global Analysis Token**
   - **Expires in**: **No expiration**
3. Click **Generate**
4. **COPIAR EL TOKEN COMPLETO** (solo se muestra una vez)
   - Ejemplo: `squ_1234567890abcdefghijklmnopqrstuvwxyz`

### Paso 4: Actualizar .env.infra
```powershell
# Editar el archivo con notepad
notepad .env.infra
```

Reemplazar la línea `SONAR_TOKEN=` con el nuevo token:
```
SONAR_TOKEN=squ_XXXXXXXXXXXX_NUEVO_TOKEN_AQUI
```

**Guardar y cerrar el archivo.**

### Paso 5: Recargar Jenkins con el nuevo token
```powershell
# Recrear Jenkins para que cargue el nuevo token desde .env.infra
docker compose -f docker-compose.ci.yml --env-file .env.infra up -d --force-recreate jenkins

# Esperar 30 segundos para que Jenkins inicie
Start-Sleep -Seconds 30

# Verificar que Jenkins está corriendo
docker ps | Select-String jenkins
```

### Paso 6: Verificar el token manualmente (OPCIONAL)
```powershell
# Probar autenticación directamente desde dentro del contenedor Jenkins
docker exec jenkins sh -c 'curl -u $SONAR_AUTH_TOKEN: http://sonarqube:9000/api/system/status'

# Debe responder algo como: {"status":"UP","version":"9.9.8.100196","id":"..."}
# Si responde "Unauthorized", el token está mal
```

### Paso 7: Probar un build en Jenkins
1. Ir a http://localhost:8081
2. Login como `admin` / `clave123`
3. Abrir cualquier pipeline (ej: `gestion-departamentos`)
4. Click en **"Construir ahora"**
5. Verificar que el stage **SonarQube** pase sin errores

## Notas Importantes
- El token en SonarQube debe ser **Global Analysis Token**
- **NO** usar tokens de tipo "User Token" o "Project Analysis Token"
- El token NO expira si seleccionas "No expiration"
- Jenkins lee el token desde la variable de entorno `SONAR_AUTH_TOKEN` que se define en `docker-compose.ci.yml`
