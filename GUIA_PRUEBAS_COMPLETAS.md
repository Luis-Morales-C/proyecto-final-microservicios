# Guía de Pruebas Completas del Proyecto

Esta guía describe cómo probar completamente el proyecto antes de subir cambios al repositorio.

## 1. Pruebas Locales de los Microservicios

### 1.1 Levantar la infraestructura completa
```powershell
# Asegurarse de estar en la raíz del proyecto
cd a:\Documents\proyecto-final-microservicios

# Detener todo si hay algo corriendo
docker compose down -v

# Levantar TODA la infraestructura (bases de datos, RabbitMQ, microservicios, gateway)
docker compose up -d --build

# Esperar 2-3 minutos para que todos los servicios inicien
Start-Sleep -Seconds 120

# Verificar que todos los servicios están corriendo
docker ps
```

**Servicios esperados:**
- Bases de datos: `database-empleados`, `database-departamentos`, `database-auth`, `database-perfiles`, `database-vacaciones`, `database-notificaciones`
- RabbitMQ: `rabbitmq`, `rabbitmq-init`
- Microservicios: `gestion-empleados`, `gestion-departamentos`, `auth-service`, `perfiles-service`, `vacaciones-service`, `notificaciones-service`
- Gateway: `api-gateway`

### 1.2 Verificar logs de cada servicio
```powershell
# Ver logs de cada servicio (reemplazar SERVICE_NAME)
docker logs gestion-empleados --tail 50
docker logs gestion-departamentos --tail 50
docker logs auth-service --tail 50
docker logs api-gateway --tail 50

# Buscar errores (no debe haber excepciones no controladas)
```

### 1.3 Probar endpoints básicos
```powershell
# Health check del gateway
curl http://localhost:8080/health

# Login del admin (debe retornar token)
curl -X POST http://localhost:8080/auth/login `
  -H "Content-Type: application/json" `
  -d '{"username":"admin","password":"admin123"}'

# Si funciona, copiar el token y probarlo en otros endpoints
$token = "PEGAR_TOKEN_AQUI"
curl http://localhost:8080/empleados `
  -H "Authorization: Bearer $token"
```

## 2. Pruebas E2E (End-to-End)

### 2.1 Ejecutar tests E2E desde el proyecto
```powershell
# Ir al directorio de tests E2E
cd e2e-tests

# Establecer la URL del gateway
$env:BASE_URL = "http://localhost:8080"

# Ejecutar los tests
npm test

# Ver reporte HTML (se genera automáticamente)
# Abrir: e2e-tests/reports/cucumber.html
```

**Resultado esperado:**
- Todos los escenarios deben pasar (verde)
- Si alguno falla, revisar logs de los servicios involucrados

### 2.2 Limpiar después de las pruebas E2E
```powershell
# Volver a la raíz
cd ..

# Detener todo
docker compose down -v
```

## 3. Pruebas de CI/CD con Jenkins Local

### 3.1 Levantar la infraestructura de CI
```powershell
# Asegurarse de que .env.infra está configurado correctamente
cat .env.infra

# Levantar Jenkins + SonarQube + Registry
docker compose -f docker-compose.ci.yml --env-file .env.infra up -d --build

# Esperar 2-3 minutos
Start-Sleep -Seconds 120

# Verificar que están corriendo
docker ps | Select-String jenkins
docker ps | Select-String sonarqube
docker ps | Select-String registry
```

### 3.2 Verificar SonarQube
```powershell
# Abrir en el navegador
Start-Process http://localhost:9000

# Login: admin / clave123
# Verificar que no haya proyectos antiguos con errores
```

**Si hay error de autenticación:**
- Seguir la guía en `GUIA_SONARQUBE_TOKEN.md`
- Regenerar el token
- Actualizar `.env.infra`
- Recrear Jenkins

### 3.3 Ejecutar builds en Jenkins
```powershell
# Abrir Jenkins en el navegador
Start-Process http://localhost:8081

# Login: admin / clave123
```

**Ejecutar builds manualmente:**
1. Abrir pipeline `gestion-empleados`
2. Click **"Construir ahora"**
3. Esperar a que termine (5-10 minutos)
4. Verificar que todos los stages pasen:
   - ✓ Checkout
   - ✓ Build
   - ✓ Test (sin tests de integración)
   - ✓ SonarQube
   - ✓ Quality Gate
   - ✓ Package
   - ✓ Publish
   - ✓ E2E Tests

**Repetir para otros microservicios:**
- `gestion-departamentos`
- `auth-service`
- Etc.

### 3.4 Verificar Quality Gates en SonarQube
```powershell
# Abrir SonarQube
Start-Process http://localhost:9000

# Verificar proyectos:
# - gestion-empleados → Quality Gate: PASSED
# - gestion-departamentos → Quality Gate: PASSED
```

## 4. Pruebas de Integración Completa (CI + E2E)

Esta es la prueba final que simula exactamente lo que hace Jenkins en CI.

### 4.1 Limpiar TODO
```powershell
# Detener servicios de desarrollo
docker compose down -v

# Detener servicios de CI
docker compose -f docker-compose.ci.yml --env-file .env.infra down -v

# Limpiar imágenes locales (opcional)
docker system prune -f
```

### 4.2 Levantar solo CI
```powershell
# Levantar Jenkins + SonarQube + Registry
docker compose -f docker-compose.ci.yml --env-file .env.infra up -d --build

# Esperar
Start-Sleep -Seconds 180
```

### 4.3 Ejecutar build completo
1. Ir a Jenkins: http://localhost:8081
2. Ejecutar build de `gestion-empleados`
3. Monitorear cada stage en tiempo real
4. Si el stage **E2E Tests** pasa → **TODO FUNCIONA**

### 4.4 Verificar artefactos generados
```powershell
# Verificar que la imagen se publicó en el registry local
curl http://localhost:5000/v2/_catalog

# Debe mostrar: {"repositories":["gestion-empleados",...]}
```

## 5. Checklist Final Antes de Subir Cambios

Marcar cada item:

- [ ] Todos los servicios levantan sin errores (`docker compose up -d`)
- [ ] Tests E2E locales pasan (`npm test` en `e2e-tests/`)
- [ ] Token de SonarQube está actualizado y válido
- [ ] Jenkins + SonarQube + Registry están corriendo
- [ ] Build de al menos UN microservicio pasa en Jenkins
- [ ] Stage **E2E Tests** en Jenkins pasa
- [ ] Quality Gate pasa en SonarQube
- [ ] No hay errores en logs de contenedores

## 6. Subir Cambios al Repositorio

```powershell
# Ver cambios pendientes
git status

# Agregar cambios relevantes
git add microservicios/gestion-empleados/Jenkinsfile
git add GUIA_SONARQUBE_TOKEN.md
git add GUIA_PRUEBAS_COMPLETAS.md

# Commit descriptivo
git commit -m "Fix: Excluir tests de integración en CI

- Tests de integración requieren Docker (Testcontainers)
- Jenkins no tiene acceso a Docker durante stage Test
- Tests de integración se ejecutan en el stage E2E
- Agregar guías de pruebas y troubleshooting"

# Push
git push origin main
```

## 7. Verificar en Jenkins Automático

Después del push:
1. Jenkins detecta el cambio automáticamente
2. Ejecuta el pipeline
3. Verificar en http://localhost:8081 que el build pase

## Notas Importantes

### Diferencia entre `docker-compose.yml` y `docker-compose.ci.yml`
- **`docker-compose.yml`**: Infraestructura de desarrollo
  - Bases de datos
  - RabbitMQ
  - Microservicios
  - Gateway
  - **USO**: Desarrollo local y pruebas manuales

- **`docker-compose.ci.yml`**: Infraestructura de CI/CD
  - Jenkins
  - SonarQube
  - Docker Registry local
  - **USO**: Pruebas automatizadas con Jenkins

### ¿Por qué no levantar todo junto?
- Jenkins construye e inicia los servicios dinámicamente durante el stage E2E
- Levantar ambos compose files causaría conflictos de puertos
- El workflow correcto es:
  1. Desarrollo/pruebas manuales → `docker-compose.yml`
  2. Detener todo
  3. CI/CD automático → `docker-compose.ci.yml`

### Troubleshooting Común
| Síntoma | Causa | Solución |
|---------|-------|----------|
| "Not authorized" en SonarQube | Token expirado/incorrecto | Ver `GUIA_SONARQUBE_TOKEN.md` |
| Tests de integración fallan | Testcontainers sin Docker | Excluir con `-Dtest=!*Integration*` |
| E2E timeout en RabbitMQ | Contenedor no listo | Aumentar timeout en `docker-compose.e2e.yml` |
| "No such network" en E2E | Red no creada | Jenkins crea la red automáticamente |
