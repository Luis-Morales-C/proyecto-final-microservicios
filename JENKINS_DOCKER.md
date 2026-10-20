# Jenkins en Docker - Documentación Técnica

## Introducción

Este documento describe la implementación de Jenkins dentro de un contenedor Docker, incluyendo las decisiones arquitectónicas, configuración de herramientas de construcción y estrategias para ejecutar Docker desde dentro del contenedor Jenkins.

## Problema: Docker dentro de Docker

Al ejecutar Jenkins en un contenedor, surge un desafío: los pipelines necesitan construir imágenes Docker, pero Docker no está diseñado para ejecutarse nativamente dentro de contenedores.

Existen dos estrategias principales para resolver este problema:

### Estrategia 1: Docker Socket Mount (Implementada)

**Descripción:**
Montar el socket Unix del daemon Docker del host dentro del contenedor Jenkins.

**Configuración:**
```yaml
volumes:
  - /var/run/docker.sock:/var/run/docker.sock
```

**Funcionamiento:**
- Jenkins ejecuta comandos docker CLI dentro del contenedor
- Los comandos se envían al daemon Docker del host a través del socket
- Las imágenes se construyen y almacenan en el daemon del host

**Ventajas:**
- Configuración simple y directa
- Menor overhead: no hay daemon Docker adicional
- Reutiliza la cache del host
- Mejor rendimiento

**Desventajas:**
- Riesgo de seguridad: el contenedor tiene control completo sobre el daemon del host
- No hay aislamiento real entre Jenkins y el Docker host
- Inadecuado para entornos de producción multi-tenant

**Decisión:**
Esta estrategia es aceptable para entornos académicos y de desarrollo donde el riesgo de seguridad es manejable.

### Estrategia 2: Docker-in-Docker (DinD) (No Implementada)

**Descripción:**
Ejecutar un daemon Docker completo dentro del contenedor Jenkins.

**Configuración típica:**
```yaml
image: docker:dind
privileged: true
```

**Funcionamiento:**
- Jenkins ejecuta comandos docker CLI
- Los comandos se procesan por el daemon Docker interno
- Aislamiento completo del daemon del host

**Ventajas:**
- Mayor aislamiento de seguridad
- Cada contenedor tiene su propio daemon

**Desventajas:**
- Mayor complejidad de configuración
- Requiere privileged mode
- Mayor consumo de recursos
- Problemas potenciales con storage drivers
- Cache no compartida

**Razón de no implementación:**
Para el contexto de este proyecto (académico), la complejidad adicional y el overhead no justifican los beneficios de aislamiento.

## Imagen Docker de Jenkins

### Imagen Base

```dockerfile
FROM jenkins/jenkins:lts-jdk21
```

**Justificación de elección:**
- Imagen oficial mantenida por la comunidad Jenkins
- LTS (Long Term Support): mayor estabilidad
- JDK 21: Versión LTS de Java requerida para microservicio gestion-empleados
- Imagen pre-configurada con plugins base y estructura de directorios

### Instalación de Plugins Jenkins

```dockerfile
RUN jenkins-plugin-cli --plugins \
    workflow-aggregator \
    pipeline-stage-view \
    docker-workflow \
    git \
    credentials-binding \
    timestamper \
    configuration-as-code \
    job-dsl \
    sonar \
    junit \
    coverage \
    cucumber-reports \
    htmlpublisher
```

**Propósito de plugins:**

| Plugin | Función |
|--------|---------|
| workflow-aggregator | Suite completa de Pipeline (incluye workflow-durable-task-step, etc.) |
| pipeline-stage-view | Visualización gráfica de stages del pipeline |
| docker-workflow | Permite usar agentes Docker y comandos docker dentro de pipelines |
| git | Integración con repositorios Git |
| credentials-binding | Manejo seguro de credenciales en pipelines |
| timestamper | Añade timestamps a logs de consola |
| configuration-as-code | Permite configurar Jenkins con archivos YAML |
| job-dsl | Creación programática de jobs |
| sonar | Integración con SonarQube |
| junit | Publicación de reportes JUnit XML |
| coverage | Visualización de cobertura de código |
| cucumber-reports | Publicación de reportes Cucumber HTML/JSON |
| htmlpublisher | Publicación de reportes HTML arbitrarios |

### Instalación de Herramientas de Sistema

#### Docker CLI y Docker Compose

```dockerfile
RUN apt-get update \
 && apt-get install -y --no-install-recommends \
    ca-certificates curl gnupg git \
    python3 python3-venv python3-pip \
 && install -m 0755 -d /etc/apt/keyrings \
 && curl -fsSL https://download.docker.com/linux/debian/gpg \
    -o /etc/apt/keyrings/docker.asc \
 && chmod a+r /etc/apt/keyrings/docker.asc \
 && echo "deb [arch=$(dpkg --print-architecture) \
    signed-by=/etc/apt/keyrings/docker.asc] \
    https://download.docker.com/linux/debian \
    $(. /etc/os-release && echo $VERSION_CODENAME) stable" \
    > /etc/apt/sources.list.d/docker.list \
 && apt-get update \
 && apt-get install -y --no-install-recommends \
    docker-ce-cli \
    docker-compose-plugin \
 && rm -rf /var/lib/apt/lists/*
```

**Componentes instalados:**
- docker-ce-cli: Cliente Docker (sin daemon)
- docker-compose-plugin: Plugin Compose v2 (comando: docker compose)

**Nota:** No se instala docker-ce (daemon) porque se utiliza el daemon del host via socket mount.

#### Node.js y npm

```dockerfile
RUN curl -fsSL https://deb.nodesource.com/setup_22.x | bash - \
 && apt-get install -y --no-install-recommends nodejs
```

**Versión:** Node.js 22.x LTS
**Propósito:** Ejecutar tests E2E con Cucumber.js

#### Maven

```dockerfile
RUN curl -fsSL \
    https://archive.apache.org/dist/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.tar.gz \
    | tar -xz -C /opt \
 && ln -s /opt/apache-maven-3.9.9/bin/mvn /usr/local/bin/mvn
```

**Versión:** Maven 3.9.9
**Propósito:** Construcción del microservicio gestion-empleados (Java/Spring Boot)

**Nota:** Se instala manualmente en lugar de usar imagen jenkins/jenkins:lts-jdk21 + Maven porque permite control preciso de la versión.

#### Python y pip

```dockerfile
RUN apt-get install -y --no-install-recommends \
    python3 python3-venv python3-pip
```

**Versión:** Python 3.x (versión del sistema Debian)
**Propósito:** Ejecución de tests del microservicio gestion-departamentos

## Configuración como Código (JCasC)

### Archivo casc.yaml

Jenkins se configura automáticamente al iniciar usando el archivo `jenkins/casc.yaml`.

#### Sección: jenkins

```yaml
jenkins:
  systemMessage: "Jenkins CI - Reto 7 (configurado con JCasC)"
  numExecutors: 1
  securityRealm:
    local:
      allowsSignup: false
      users:
        - id: "admin"
          password: "${JENKINS_ADMIN_PASSWORD:-admin123}"
  authorizationStrategy:
    loggedInUsersCanDoAnything:
      allowAnonymousRead: false
```

**numExecutors: 1**
- Limita ejecución de builds a uno a la vez
- Necesario porque los tests E2E levantan docker compose en puertos fijos
- Evita conflictos de puertos entre pipelines concurrentes

**securityRealm**
- Modo: local (usuarios almacenados en Jenkins, no LDAP/AD)
- allowsSignup: false (solo usuarios pre-configurados)
- Password desde variable de entorno con fallback

**authorizationStrategy**
- Usuarios autenticados tienen permisos completos
- Sin acceso anónimo (allowAnonymousRead: false)

#### Sección: credentials

```yaml
credentials:
  system:
    domainCredentials:
      - credentials:
          - string:
              scope: GLOBAL
              id: "sonar-token"
              description: "Token de SonarQube"
              secret: "${SONAR_TOKEN:-pendiente}"
```

**Tipo:** Secret text
**Uso:** Autenticación en SonarQube desde pipeline
**Scope:** GLOBAL (disponible para todos los jobs)

#### Sección: unclassified

```yaml
unclassified:
  location:
    url: "http://localhost:9090/"
  sonarGlobalConfiguration:
    buildWrapperEnabled: true
    installations:
      - name: "sonarqube"
        serverUrl: "http://sonarqube:9000"
        credentialsId: "sonar-token"
```

**location.url**
- URL base de Jenkins
- Usado en notificaciones y webhooks

**sonarGlobalConfiguration**
- buildWrapperEnabled: Habilita análisis incremental
- serverUrl: Endpoint de SonarQube (nombre de contenedor en ci-network)
- credentialsId: Referencia al token configurado

#### Sección: jobs

```yaml
jobs:
  - script: |
      pipelineJob('gestion-empleados') {
        description('Java / Maven - build, test, Sonar, Docker, E2E')
        definition {
          cpsScm {
            scm {
              git {
                remote { url('${REPO_URL}') }
                branches('*/${REPO_BRANCH:-main}')
              }
            }
            scriptPath('microservicios/gestion-empleados/Jenkinsfile')
          }
        }
      }
```

**Job DSL:**
- Crea jobs programáticamente desde código Groovy
- pipelineJob: Define un pipeline job
- cpsScm: Pipeline from SCM (Continuous Pipeline Script from SCM)
- scriptPath: Ruta del Jenkinsfile en el repositorio

**Ventaja:**
Los Jenkinsfile están versionados en Git junto con el código del microservicio.

## Sintaxis de Pipeline: Declarativo vs Scripted

Jenkins soporta dos sintaxis para definir pipelines.

### Pipeline Declarativo (Implementado)

**Estructura:**
```groovy
pipeline {
    agent any
    options { }
    environment { }
    stages {
        stage('Nombre') {
            steps { }
        }
    }
    post { }
}
```

**Características:**
- Sintaxis estructurada con secciones predefinidas
- Validación automática de sintaxis
- Visualización gráfica clara en Jenkins UI
- Menor curva de aprendizaje
- Extensible mediante directivas y steps

**Casos de uso:**
- Pipelines estándar de CI/CD
- Equipos con variados niveles de experiencia
- Proyectos donde mantenibilidad es prioridad

### Pipeline Scripted (No Implementado)

**Estructura:**
```groovy
node {
    stage('Nombre') {
        // Groovy completo
    }
}
```

**Características:**
- Sintaxis basada en Groovy completo
- Máxima flexibilidad
- Lógica arbitrariamente compleja
- Require conocimiento de Groovy
- Sin validación automática

**Casos de uso:**
- Workflows muy complejos con lógica condicional extensa
- Necesidad de abstracciones personalizadas
- Equipos con fuerte expertise en Groovy

**Razón de elección del Declarativo:**
Para este proyecto, la sintaxis declarativa es suficiente y proporciona mejor legibilidad y mantenibilidad.

## Integración con Docker Host

### Configuración en docker-compose.ci.yml

```yaml
jenkins:
  build:
    context: ./jenkins
    dockerfile: Dockerfile
  container_name: jenkins
  user: root
  volumes:
    - /var/run/docker.sock:/var/run/docker.sock
    - jenkins_data:/var/jenkins_home
  networks:
    - ci-network
  extra_hosts:
    - "host.docker.internal:host-gateway"
  ports:
    - "9090:8080"
    - "50000:50000"
  environment:
    - JENKINS_OPTS=--httpPort=8080
    - CASC_JENKINS_CONFIG=/var/jenkins_home/casc_configs
```

**Elementos clave:**

**user: root**
- Necesario para acceder al socket Docker que usualmente requiere permisos root
- Alternativa: Añadir jenkins user al grupo docker del host (más complejo en entorno académico)

**Socket Mount**
```yaml
volumes:
  - /var/run/docker.sock:/var/run/docker.sock
```
- Punto de montaje del socket Unix
- Permite ejecutar comandos docker desde Jenkins

**Volume para Workspace**
```yaml
volumes:
  - jenkins_data:/var/jenkins_home
```
- Persistencia de configuración, jobs, workspace
- Incluye cache de Maven (.m2), npm, pip

**extra_hosts**
```yaml
extra_hosts:
  - "host.docker.internal:host-gateway"
```
- Permite a Jenkins referenciar servicios del host
- Útil para acceder a registry en localhost:5000

**Puertos:**
- 8080: UI web de Jenkins (mapeado a 9090 en host)
- 50000: Puerto JNLP para agentes Jenkins (no usado en este proyecto)

## Ejecución de Docker desde Pipelines

### Comandos Docker en Jenkinsfile

Ejemplo de construcción de imagen:

```groovy
stage('Package') {
    steps {
        dir("microservicios/gestion-empleados") {
            sh """
                docker build \
                  -t localhost:5000/proyecto-final/gestion-empleados:${BUILD_NUMBER}-${GIT_COMMIT_SHORT} \
                  -t localhost:5000/proyecto-final/gestion-empleados:latest \
                  .
            """
        }
    }
}
```

**Flujo de ejecución:**
1. Jenkins ejecuta sh step dentro del contenedor
2. Comando docker build se ejecuta en el contenedor Jenkins
3. Docker CLI se comunica con daemon del host via socket
4. Imagen se construye en el daemon del host
5. Imagen queda disponible en el host

### Docker Compose para E2E

Ejemplo de levantamiento de aplicación:

```groovy
stage('E2E Tests') {
    steps {
        sh './scripts/ci-e2e-up.sh'
        sh 'docker network connect proyecto-final-microservicios_default jenkins'
        dir('e2e-tests') {
            sh 'npm ci'
            sh 'npm test'
        }
    }
    post {
        always {
            sh './scripts/ci-e2e-down.sh'
        }
    }
}
```

**Consideraciones:**

**Network Injection:**
```bash
docker network connect proyecto-final-microservicios_default jenkins
```
- Conecta contenedor Jenkins a la red de la aplicación
- Permite acceso a servicios por nombre de contenedor
- Desconectado en post steps

**Limpieza Garantizada:**
```groovy
post {
    always { }
}
```
- El bloque always se ejecuta incluso si tests fallan
- Asegura que contenedores se eliminen
- Evita conflictos en siguientes ejecuciones

## Volúmenes y Cache

### Cache de Maven

```
jenkins_data:/var/jenkins_home
  └── .m2/repository/
```

- Dependencias Maven descargadas persisten entre builds
- Reduce tiempo de build subsecuente
- Primera ejecución: descarga completa
- Ejecuciones posteriores: solo nuevas dependencias

### Cache de npm

```
jenkins_data:/var/jenkins_home
  └── .npm/
```

- Paquetes npm cacheados
- Reduce tiempo de instalación de dependencias E2E

### Cache de pip

```
jenkins_data:/var/jenkins_home
  └── .cache/pip/
```

- Paquetes Python cacheados
- Mejora velocidad de creación de virtualenv

## Mantenimiento y Troubleshooting

### Verificar Acceso al Socket Docker

Desde contenedor Jenkins:

```bash
docker exec jenkins docker ps
```

Debe listar los contenedores del host.

### Verificar Instalación de Herramientas

```bash
docker exec jenkins mvn -version
docker exec jenkins node -v
docker exec jenkins python3 --version
docker exec jenkins docker --version
docker exec jenkins docker compose version
```

### Reiniciar Jenkins sin Perder Datos

```bash
docker compose -f docker-compose.ci.yml restart jenkins
```

El volumen jenkins_data preserva toda la configuración.

### Limpiar Workspace

Desde Jenkins UI:
- Job → Workspace → Wipe Out Workspace

Desde CLI:

```bash
docker exec jenkins rm -rf /var/jenkins_home/workspace/NOMBRE_JOB
```

### Backup de Jenkins

Backup completo del volumen:

```bash
docker run --rm \
  -v reto7-ci_jenkins_data:/data \
  -v $(pwd):/backup \
  busybox tar czf /backup/jenkins-backup.tar.gz /data
```

Restauración:

```bash
docker run --rm \
  -v reto7-ci_jenkins_data:/data \
  -v $(pwd):/backup \
  busybox tar xzf /backup/jenkins-backup.tar.gz -C /
```

## Seguridad

### Riesgos del Socket Mount

**Problema:**
El contenedor Jenkins con acceso al socket Docker puede:
- Crear/eliminar cualquier contenedor en el host
- Acceder a volúmenes de otros contenedores
- Ejecutar contenedores con privilegios
- Efectivamente, tiene acceso root al host

**Mitigaciones en Producción (no implementadas en este proyecto):**
1. Usar Docker-in-Docker en lugar de socket mount
2. Ejecutar Jenkins en VM dedicada, no en host de producción
3. Implementar SELinux/AppArmor policies
4. Usar Kubernetes con RBAC para limitar permisos

**Justificación para Entorno Académico:**
El riesgo es aceptable porque:
- Jenkins solo es accesible localmente
- No hay datos sensibles
- Prioridad en simplicidad de configuración

### Variables de Entorno Sensibles

**Práctica implementada:**
```yaml
environment:
  - JENKINS_ADMIN_PASSWORD=${JENKINS_ADMIN_PASSWORD}
  - SONAR_TOKEN=${SONAR_TOKEN}
```

- Valores no hardcodeados en archivos versionados
- Leídos desde .env.infra (en .gitignore)
- Interpolados en JCasC vía ${VAR_NAME}

## Conclusiones

### Decisiones Arquitectónicas

1. Socket Mount sobre DinD: Simplicidad y performance sobre aislamiento máximo
2. Pipeline Declarativo sobre Scripted: Mantenibilidad sobre flexibilidad máxima
3. JCasC sobre configuración manual: Reproducibilidad y versionado
4. Un executor sobre múltiples: Evitar conflictos de puertos en E2E

### Herramientas Instaladas

| Herramienta | Versión | Propósito |
|-------------|---------|-----------|
| JDK | 21 | Compilación de gestion-empleados |
| Maven | 3.9.9 | Build tool para Java |
| Node.js | 22.x | Ejecución de tests E2E |
| Python | 3.x | Tests de gestion-departamentos |
| Docker CLI | Latest | Construcción y gestión de imágenes |
| Docker Compose | v2 | Orquestación para E2E |

### Validación de Implementación

Verificar que todo funciona correctamente:

```bash
# 1. Jenkins responde
curl -f http://localhost:9090/login

# 2. Jenkins puede ejecutar Docker
docker exec jenkins docker ps

# 3. Herramientas instaladas
docker exec jenkins bash -c "mvn -v && node -v && python3 --version"

# 4. JCasC aplicado
docker exec jenkins cat /var/jenkins_home/config.xml | grep "Jenkins CI - Reto 7"
```

Si todos los comandos tienen éxito, la configuración es correcta.
