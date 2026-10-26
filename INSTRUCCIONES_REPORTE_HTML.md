# Reporte HTML de Cucumber - Configuración

## ✅ Estado: Configurado

El reporte HTML de Cucumber ya está completamente configurado en el proyecto:

### 1. publishHTML en Jenkinsfiles

Ambos servicios (`gestion-empleados` y `gestion-departamentos`) tienen configurado `publishHTML` en la etapa E2E:

```groovy
publishHTML(target: [
    reportDir            : 'e2e-tests/reports',
    reportFiles          : 'cucumber.html',
    reportName           : 'Reporte BDD (HTML)',
    reportTitles         : 'Reporte BDD',
    keepAll              : true,
    alwaysLinkToLastBuild: true,
    allowMissing         : true
])
```

### 2. CSP Relajada en Jenkins

El servicio Jenkins en `docker-compose.ci.yml` tiene la Content Security Policy desactivada:

```yaml
environment:
  JAVA_OPTS: "-Djenkins.install.runSetupWizard=false -Dhudson.model.DirectoryBrowserSupport.CSP="
```

## 🚀 Aplicar los Cambios

Para que los cambios surtan efecto, recrea el contenedor de Jenkins:

```bash
docker compose -f docker-compose.ci.yml --env-file .env.infra down
docker compose -f docker-compose.ci.yml --env-file .env.infra up -d
```

## 📊 Resultado Esperado

Después de ejecutar un build:

1. En la página del build verás un nuevo enlace: **"Reporte BDD (HTML)"**
2. Al hacer clic, se mostrará el reporte HTML completo de Cucumber con:
   - Estilos CSS aplicados correctamente
   - JavaScript funcionando
   - Gráficos y estadísticas visuales
   - Navegación por escenarios y pasos

## 📝 Notas Importantes

### Seguridad de la CSP

- **CSP vacía** (`CSP=""`) desactiva completamente la Content Security Policy
- Esto permite que scripts inline, estilos y recursos externos funcionen sin restricciones
- Es **aceptable en entornos académicos y de desarrollo**
- Para producción, considera una política más restrictiva o usa el reporte JSON/JUnit

### Alternativa: CSP Permisiva (Opcional)

Si prefieres no desactivar completamente la CSP, puedes usar una política más permisiva:

```
default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src 'self' data:
```

Sin embargo, esto requiere configuración adicional en Jenkins (script init.groovy.d) y para el reto la CSP vacía es suficiente.

## 🔍 Verificación

1. Ejecuta un build en cualquiera de los servicios
2. Ve a la página del build
3. Busca el enlace "Reporte BDD (HTML)" en el menú lateral
4. El reporte debe mostrarse completamente formateado con estilos

## 📦 Otros Reportes Disponibles

El pipeline también genera:

- **Cucumber JSON Plugin**: Vista interactiva con gráficos de tendencias
- **Test Results**: Vista estándar de JUnit con historial
- **Artifacts**: `cucumber.html`, `cucumber.json`, `junit.xml` descargables
