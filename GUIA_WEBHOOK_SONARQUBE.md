# Configurar Webhook de SonarQube → Jenkins

## ¿Para qué sirve el webhook?

El webhook permite que **SonarQube notifique a Jenkins** cuando termine el análisis de código, para que el stage **Quality Gate** pueda continuar.

**Sin webhook:** Jenkins se queda esperando indefinidamente en el stage Quality Gate → **TIMEOUT**

**Con webhook:** SonarQube envía resultado → Jenkins continúa automáticamente → **SUCCESS**

---

## Paso a Paso: Configurar Webhook

### 1. Verificar que SonarQube está corriendo

```powershell
docker ps | Select-String sonarqube
# Debe mostrar: sonarqube corriendo en puerto 9000
```

Si no está corriendo:
```powershell
docker compose -f docker-compose.ci.yml --env-file .env.infra up -d sonarqube
Start-Sleep -Seconds 120  # Esperar 2 minutos
```

### 2. Acceder a SonarQube

1. Abrir en el navegador: http://localhost:9000
2. Login:
   - **Usuario**: `admin`
   - **Password**: `clave123` (o el valor de `SONAR_DB_PASSWORD` en `.env.infra`)

### 3. Ir a la configuración de Webhooks

**Ruta en la UI:**
1. Click en **"Administration"** (menú superior)
2. En el menú lateral, sección **"Configuration"** → **"Webhooks"**
3. Click en **"Create"**

### 4. Crear el Webhook

Completar el formulario con estos valores:

| Campo | Valor | Explicación |
|-------|-------|-------------|
| **Name** | `jenkins-webhook` | Nombre descriptivo |
| **URL** | `http://jenkins:8080/sonarqube-webhook/` | URL INTERNA de Jenkins (nota la `/` al final) |
| **Secret** | *(dejar vacío)* | No es necesario en entorno local |

**⚠️ IMPORTANTE:**
- La URL debe ser `http://jenkins:8080/sonarqube-webhook/` (con la barra `/` al final)
- Usar `jenkins` (nombre del contenedor), **NO** `localhost:8081`
- Ambos contenedores están en la misma red Docker (`ci-network`), por eso se comunican por nombre

### 5. Guardar y verificar

1. Click en **"Create"**
2. Debe aparecer el webhook en la lista con estado **verde** (activo)

### 6. Probar el webhook (OPCIONAL)

Crear un análisis de prueba para verificar que el webhook funciona:

```powershell
# Desde dentro del contenedor Jenkins
docker exec jenkins sh -c '
cd /tmp
echo "sonar.projectKey=test-webhook" > sonar-project.properties
echo "sonar.sources=." >> sonar-project.properties
sonar-scanner \
  -Dsonar.host.url=http://sonarqube:9000 \
  -Dsonar.token=$SONAR_AUTH_TOKEN
'
```

Luego verificar en SonarQube:
1. Ir a **Administration** → **Webhooks**
2. Click en el webhook `jenkins-webhook`
3. Ver sección **"Recent Deliveries"**
4. Debe aparecer una entrega reciente con código **200 OK**

---

## Verificación Completa

### Probar con un Build Real en Jenkins

1. Ir a Jenkins: http://localhost:8081
2. Login: `admin` / `clave123`
3. Abrir pipeline `gestion-empleados`
4. Click **"Construir ahora"**
5. Monitorear el build

**Resultado esperado en stage Quality Gate:**
```
Checking Quality Gate status
Quality Gate: OK
Quality Gate passed
```

**Si hay error:**
```
Timeout while waiting for Quality Gate
```
→ El webhook NO está configurado o la URL es incorrecta

---

## Troubleshooting

### Error: "Timeout while waiting for Quality Gate"

**Causas posibles:**

1. **Webhook no configurado**
   - Solución: Seguir los pasos 1-5 arriba

2. **URL incorrecta del webhook**
   - ❌ INCORRECTO: `http://localhost:8081/sonarqube-webhook/`
   - ✅ CORRECTO: `http://jenkins:8080/sonarqube-webhook/`

3. **Falta la barra `/` al final**
   - ❌ INCORRECTO: `http://jenkins:8080/sonarqube-webhook`
   - ✅ CORRECTO: `http://jenkins:8080/sonarqube-webhook/`

4. **Los contenedores no están en la misma red**
   ```powershell
   # Verificar que ambos están en ci-network
   docker network inspect ci-network
   # Debe mostrar: jenkins Y sonarqube en "Containers"
   ```

### Error: "Failed to connect to Jenkins"

SonarQube no puede alcanzar a Jenkins:

```powershell
# Verificar conectividad DESDE SonarQube HACIA Jenkins
docker exec sonarqube sh -c 'curl -f http://jenkins:8080/login'

# Debe responder HTML de Jenkins
```

Si falla:
```powershell
# Recrear ambos servicios en la misma red
docker compose -f docker-compose.ci.yml --env-file .env.infra down
docker compose -f docker-compose.ci.yml --env-file .env.infra up -d
```

### Verificar Recent Deliveries en SonarQube

Después de cada análisis, verificar en SonarQube:
1. **Administration** → **Webhooks**
2. Click en `jenkins-webhook`
3. Ver **"Recent Deliveries"**

**Códigos de respuesta:**
- **200 OK** → ✅ Webhook funcionó correctamente
- **404 Not Found** → ❌ URL incorrecta (verificar `/sonarqube-webhook/`)
- **Connection refused** → ❌ Jenkins no está corriendo o red incorrecta
- **Timeout** → ❌ Problema de red entre contenedores

---

## Resumen: Checklist de Configuración

Marcar cada item:

- [ ] SonarQube está corriendo (`docker ps | Select-String sonarqube`)
- [ ] Token de SonarQube está actualizado en `.env.infra`
- [ ] Jenkins está corriendo (`docker ps | Select-String jenkins`)
- [ ] Ambos están en la red `ci-network` (`docker network inspect ci-network`)
- [ ] Webhook creado en SonarQube con URL: `http://jenkins:8080/sonarqube-webhook/`
- [ ] Webhook tiene estado verde (activo)
- [ ] Build de prueba ejecutado en Jenkins
- [ ] Stage Quality Gate pasa sin timeout

---

## Configuración Automática (Avanzado)

Si quieres automatizar la creación del webhook con un script:

```powershell
# Obtener el token de admin en SonarQube
$sonarToken = "TU_TOKEN_AQUI"  # Del archivo .env.infra

# Crear webhook automáticamente
curl -X POST "http://localhost:9000/api/webhooks/create" `
  -u "${sonarToken}:" `
  -d "name=jenkins-webhook" `
  -d "url=http://jenkins:8080/sonarqube-webhook/"

# Verificar que se creó
curl "http://localhost:9000/api/webhooks/list" `
  -u "${sonarToken}:"
```

**Nota:** Esto requiere que el token tenga permisos de administrador.
