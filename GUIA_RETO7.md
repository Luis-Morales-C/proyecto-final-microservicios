# Guía Reto 7: Pipeline de CI/CD con Jenkins

## Descripción

Implementación de un pipeline de Integración Continua (CI) y Entrega Continua (CD) usando Jenkins para automatizar el proceso de construcción, pruebas, análisis de calidad de código y despliegue de los microservicios del proyecto.

## Arquitectura de CI/CD

### Componentes Principales

**Jenkins (puerto 9090)**
- Servidor de automatización configurado con Jenkins Configuration as Code (JCasC)
- Ejecuta pipelines declarativos para cada microservicio
- Configurado con un único executor (numExecutors: 1) para evitar conflictos de puertos durante tests E2E

**SonarQube (puerto 9000)**
- Plataforma de análisis estático de código
- Implementa Quality Gates para bloquear builds con deuda técnica
- Utiliza PostgreSQL como base de datos

**Docker Registry (puerto 5000)**
- Registro local para almacenar imágenes Docker generadas
- Permite versionado de imágenes usando build number y git commit SHA

### Topología de Red

```
ci-network (Docker bridge network)
├── jenkins (con acceso al Docker socket del host)
├── sonarqube
├── sonar-db (PostgreSQL)
└── registry

Volúmenes persistentes:
├── jenkins_data (workspace, cache de Maven/npm/pip)
├── sonarqube_data
├── sonarqube_logs
├── sonarqube_extensions
├── sonar_db_data
└── registry_data
```

## Configuración Inicial

### Paso 1: Configurar Variables de Entorno

Crear el archivo `.env.infra` basado en el ejemplo proporcionado:

```bash
cp .env.infra.example .env.infra
```

Editar `.env.infra` con los valores correspondientes:

```env
REPO_URL=https://github.com/USUARIO/proyecto-final-microservicios.git
REPO_BRANCH=main
JENKINS_ADMIN_PASSWORD=password-seguro
SONAR_DB_PASSWORD=password-sonar
SONAR_TOKEN=
```

Nota: El campo SONAR_TOKEN se completará después de configurar SonarQube.

### Paso 2: Levantar Infraestructura

Ejecutar el comando para iniciar todos los servicios de CI:

```bash
docker compose -f docker-compose.ci.yml --env-file .env.infra up -d --build
```

Este comando levanta:
- Contenedor Jenkins con configuración automática via JCasC
- Contenedores SonarQube y PostgreSQL
- Contenedor Docker Registry

El proceso de inicio completo toma aproximadamente 2-3 minutos.

### Paso 3: Acceder a Jenkins

Acceder a http://localhost:9090

Credenciales:
- Usuario: admin
- Contraseña: valor configurado en JENKINS_ADMIN_PASSWORD

La configuración JCasC habrá creado automáticamente dos pipelines:
- gestion-empleados
- gestion-departamentos

### Paso 4: Configurar SonarQube

Acceder a http://localhost:9000

Credenciales iniciales:
- Usuario: admin
- Contraseña: admin

El sistema solicitará cambio de contraseña en el primer acceso.

#### Generación de Token

1. Navegar a: My Account → Security → Generate Tokens
2. Nombre del token: jenkins-token
3. Tipo: Global Analysis Token
4. Copiar el token generado

#### Actualizar Configuración

Editar `.env.infra` y agregar el token:

```env
SONAR_TOKEN=squ_1234567890abcdef...
```

Recrear el contenedor Jenkins para aplicar el cambio:

```bash
docker compose -f docker-compose.ci.yml --env-file .env.infra up -d jenkins
```

## Estructura de los Pipelines

### Sintaxis Declarativa

Ambos pipelines utilizan la sintaxis Pipeline Declarativa de Jenkins, caracterizada por:

- Estructura basada en bloques predefinidos (pipeline, stages, stage, steps)
- Mayor legibilidad y mantenibilidad comparado con Pipeline Scripted
- Validación automática de sintaxis
- Visualización gráfica en la interfaz de Jenkins

Comparación con Pipeline Scripted:

| Aspecto | Declarativo | Scripted |
|---------|-------------|----------|
| Sintaxis base | pipeline {} | node {} |
| Lenguaje | DSL estructurado | Groovy completo |
| Complejidad | Menor curva de aprendizaje | Requiere conocer Groovy |
| Validación | Automática | Manual |
| Uso recomendado | Mayoría de casos | Casos muy complejos |

Estructura básica de un pipeline declarativo:

```groovy
pipeline {
    agent any
    options { timestamps() }
    environment { 
        VARIABLE = 'valor' 
    }
    stages {
        stage('Nombre Etapa') {
            steps {
                sh 'comando'
            }
        }
    }
    post {
        success { echo 'Pipeline exitoso' }
        failure { echo 'Pipeline fallido' }
    }
}
```

### Pipeline de gestion-empleados (Java/Maven)

Flujo de ejecución:

```
Checkout → Build → Test → SonarQube → Quality Gate → Package → Publish → E2E
```

Descripción de etapas:

**Checkout**
- Clona el repositorio desde la URL configurada en JCasC
- Utiliza la rama especificada en REPO_BRANCH

**Build**
- Ejecuta compilación Maven: `mvn clean compile`
- Prepara los artefactos para testing

**Test**
- Ejecuta pruebas unitarias y de integración con: `mvn test`
- Utiliza Testcontainers para pruebas con dependencias reales
- Genera reportes JUnit XML y cobertura JaCoCo
- Política: Falla el pipeline si hay tests fallidos

**SonarQube**
- Ejecuta análisis de código con: `mvn sonar:sonar`
- Envía métricas de código, cobertura y duplicación
- Utiliza el token configurado para autenticación

**Quality Gate**
- Espera resultado del Quality Gate de SonarQube (timeout: 5 minutos)
- Política: Falla el pipeline si el Quality Gate no pasa

**Package**
- Construye imagen Docker con `docker build`
- Aplica dos tags:
  - `BUILD_NUMBER-GIT_SHA` (ejemplo: 42-a1b2c3d)
  - `latest`

**Publish**
- Sube imagen al registry local: `docker push localhost:5000/proyecto-final/gestion-empleados`
- Publica ambos tags

**E2E Tests**
- Levanta aplicación completa con `docker compose up`
- Ejecuta suite Cucumber completa
- Genera reportes HTML, JSON y JUnit
- Limpia contenedores y volúmenes al finalizar

### Pipeline de gestion-departamentos (Python/FastAPI)

Flujo idéntico al pipeline Java:

```
Checkout → Build → Test → SonarQube → Quality Gate → Package → Publish → E2E
```

Diferencias específicas de Python:

**Build**
- Crea entorno virtual: `python3 -m venv venv`
- Instala dependencias: `pip install -r requirements-dev.txt`

**Test**
- Ejecuta pytest con cobertura: `pytest --cov=app --cov-report=xml`
- Genera reporte de cobertura en formato XML para SonarQube

**SonarQube**
- Ejecuta `sonar-scanner` en contenedor efímero
- Monta workspace usando `--volumes-from jenkins`
- Conectado a ci-network para comunicación con SonarQube

## Scripts de Soporte

### scripts/ci-e2e-up.sh

Propósito: Preparar entorno para tests E2E

Funciones:
1. Copia .env.ci a .env (variables específicas de CI)
2. Detiene y limpia contenedores previos: `docker compose down -v`
3. Levanta aplicación completa: `docker compose up -d --build`
4. Conecta contenedor Jenkins a la red de aplicación
5. Ejecuta polling hasta que todos los servicios estén healthy

### scripts/ci-e2e-down.sh

Propósito: Limpieza post-ejecución de tests E2E

Funciones:
1. Captura logs de todos los contenedores en docker-logs.txt
2. Desconecta Jenkins de la red de aplicación
3. Detiene y elimina contenedores: `docker compose down -v`

### scripts/wait-for-healthy.sh

Propósito: Espera activa hasta que el sistema esté operativo

Implementación:
- Verifica health checks de contenedores Docker
- Verifica disponibilidad de endpoints HTTP:
  - http://api-gateway:8080/health
  - http://gestion-empleados:8081/v3/api-docs
- Timeout configurable via WAIT_TIMEOUT (default: 300 segundos)
- Polling interval: 5 segundos

## Variables de Entorno para CI

El archivo `.env.ci` contiene configuración específica para ejecución en pipelines.

Diferencias clave con .env de desarrollo:

```env
# Credenciales específicas de CI
POSTGRES_PASSWORD=postgres_ci
RABBITMQ_DEFAULT_PASS=rabbit_ci

# Configuración de testing
ENABLE_DEV_ENDPOINTS=true
POLL_MAX_ATTEMPTS=30

# Seguridad (solo para CI, no usar en producción)
JWT_SECRET=ci-secret-key-123
```

## Ejecución de Pipelines

### Opción 1: Ejecución Manual

1. Acceder a http://localhost:9090
2. Seleccionar el pipeline deseado
3. Click en "Build Now"

### Opción 2: Ejecución Automática

Descomentar en los Jenkinsfiles la sección:

```groovy
triggers {
    pollSCM('H/5 * * * *')  // Polling cada 5 minutos
}
```

Jenkins ejecutará automáticamente el pipeline al detectar cambios en el repositorio Git.

## Visualización de Resultados

### Reportes en Jenkins

**Test Results**
- Reportes JUnit de todas las pruebas (unitarias, integración, E2E)
- Historial de ejecuciones con tendencias
- Detalles de tests fallidos con stack traces

**Code Coverage**
- Gráficas de cobertura de código
- Comparación con builds anteriores
- Detalles por paquete/módulo

**Cucumber Reports**
- Escenarios BDD ejecutados
- Pasos con capturas de pantalla
- Tiempo de ejecución por feature

**Artifacts**
- Reportes HTML descargables
- Logs de contenedores Docker
- Archivos de configuración generados

### Análisis en SonarQube

**Quality Gate**
- Estado del análisis (passed/failed)
- Condiciones evaluadas

**Métricas de Calidad**
- Code Smells: Issues de mantenibilidad
- Bugs: Issues de confiabilidad
- Vulnerabilities: Issues de seguridad
- Security Hotspots: Puntos a revisar
- Coverage: Porcentaje de cobertura
- Duplications: Código duplicado

## Políticas de Calidad

### Criterios de Fallo

El pipeline se marca como FAILURE y se detiene si:

1. Existen tests unitarios o de integración fallidos (etapa Test)
2. El Quality Gate de SonarQube no pasa (etapa Quality Gate)

### Criterios de Inestabilidad

El pipeline se marca como UNSTABLE pero continúa si:

1. Hay escenarios E2E fallidos (los reportes se publican para análisis)

### Configuración de Reportes

**JUnit**
- Parámetro allowEmptyResults: false
- Falla el pipeline si no se encuentran reportes

**Cucumber**
- Parámetro buildStatus: UNSTABLE
- Publica reportes incluso con escenarios fallidos

**Coverage**
- Se registra en cada build
- Permite tracking histórico de tendencias

## Debugging y Troubleshooting

### Logs de Jenkins

```bash
docker logs -f jenkins
```

### Logs de SonarQube

```bash
docker logs -f sonarqube
```

### Logs de Pipeline E2E

Los logs se archivan automáticamente como artifacts en cada build:
1. Navegar al build en Jenkins
2. Click en "Artifacts"
3. Descargar docker-logs.txt

### Acceso a Contenedor Jenkins

```bash
docker exec -it jenkins bash
```

### Verificar Conectividad

Desde contenedor Jenkins a SonarQube:

```bash
docker exec jenkins curl -f http://sonarqube:9000/api/system/health
```

### Problemas Comunes

**Error: "Cannot connect to Docker daemon"**
Causa: Socket de Docker no montado o permisos incorrectos
Solución: Verificar mount de /var/run/docker.sock en docker-compose.ci.yml

**Error: "Quality Gate timeout"**
Causa: SonarQube no envía webhook o Jenkins no lo recibe
Solución: Verificar que ambos estén en ci-network y SONAR_VALIDATEWEBHOOKS=false

**Error: "localhost:5000 unreachable"**
Causa: Jenkins intenta push a registry pero localhost no resuelve correctamente
Solución: Ya configurado con extra_hosts en docker-compose.ci.yml

## Mantenimiento

### Limpiar Imágenes del Registry

Ver imágenes almacenadas:

```bash
curl http://localhost:5000/v2/_catalog
```

Eliminar todas las imágenes (requiere recrear volumen):

```bash
docker volume rm reto7-ci_registry_data
```

### Reinicio Completo de Infraestructura

```bash
docker compose -f docker-compose.ci.yml --env-file .env.infra down -v
docker compose -f docker-compose.ci.yml --env-file .env.infra up -d --build
```

### Backup de Configuración Jenkins

```bash
docker cp jenkins:/var/jenkins_home/casc_configs/casc.yaml jenkins_backup.yaml
```

## Versionado de Imágenes

Estrategia de etiquetado implementada:

```
localhost:5000/proyecto-final/SERVICIO:BUILD_NUMBER-GIT_SHA
localhost:5000/proyecto-final/SERVICIO:latest
```

Ejemplo:

```
localhost:5000/proyecto-final/gestion-empleados:42-a1b2c3d
localhost:5000/proyecto-final/gestion-empleados:latest
```

Beneficios:
- Permite rollback a versiones específicas
- Trazabilidad: asocia cada imagen con un commit específico
- Facilita deploys reproducibles

## Conceptos Técnicos Implementados

### Docker Socket Mount Strategy

Estrategia seleccionada para ejecutar Docker dentro de Jenkins:

```yaml
volumes:
  - /var/run/docker.sock:/var/run/docker.sock
```

Características:
- Jenkins ejecuta comandos Docker usando el daemon del host
- No requiere daemon Docker dentro del contenedor (DinD)
- Menor complejidad y overhead
- Adecuado para entornos de desarrollo y CI académico

Alternativa no implementada:
- Docker-in-Docker (DinD): Mayor aislamiento pero más complejo y lento

### Jenkins Configuration as Code (JCasC)

Configuración declarativa en jenkins/casc.yaml que define:
- Usuarios y sistema de autenticación
- Credenciales para SonarQube
- Configuración de plugins
- Definición de jobs mediante Job DSL

Ventajas:
- Reproducible: La configuración está en control de versiones
- Sin pasos manuales: Jenkins configurado automáticamente al iniciar
- Facilita disaster recovery

### Quality Gates

Integración Jenkins-SonarQube mediante:
- Webhook de SonarQube a Jenkins
- Plugin sonar-quality-gates en Jenkins
- Bloqueo automático de pipeline si no se cumplen umbrales

Configuración en SonarQube:
- Coverage mínima
- Code Smells permitidos
- Duplicación máxima
- Vulnerabilidades: cero tolerancia

### Network Injection para E2E

Técnica implementada para tests E2E:

```bash
docker network connect proyecto-final-microservicios_default jenkins
```

Permite:
- Jenkins accede a servicios de aplicación por nombre de contenedor
- Tests E2E ejecutados desde Jenkins pueden alcanzar endpoints internos
- Aislamiento: solo conectado durante ejecución de tests

## Verificación de Implementación

Checklist de validación:

- [ ] Archivo .env.infra creado y configurado
- [ ] Infraestructura levantada con docker-compose.ci.yml
- [ ] Jenkins accesible en http://localhost:9090
- [ ] SonarQube accesible en http://localhost:9000
- [ ] Token de SonarQube generado y configurado
- [ ] Pipeline gestion-empleados ejecutado con éxito
- [ ] Pipeline gestion-departamentos ejecutado con éxito
- [ ] Reportes de tests visibles en Jenkins
- [ ] Proyectos visibles en SonarQube con métricas
- [ ] Imágenes publicadas en registry (verificar con curl)
- [ ] Tests E2E ejecutados y reportes generados

## Referencias Técnicas

- Jenkins Pipeline Syntax: https://www.jenkins.io/doc/book/pipeline/syntax/
- Jenkins Configuration as Code: https://github.com/jenkinsci/configuration-as-code-plugin
- SonarQube Quality Gates: https://docs.sonarqube.org/latest/user-guide/quality-gates/
- Docker Registry HTTP API: https://docs.docker.com/registry/spec/api/
