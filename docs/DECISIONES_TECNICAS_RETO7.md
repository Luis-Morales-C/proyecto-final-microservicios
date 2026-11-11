# Decisiones Técnicas - Reto 7: Integración Continua con Jenkins

Este documento explica las decisiones técnicas tomadas durante la implementación del Reto 7, organizadas por cada punto del reto.

---

## 📋 Tabla de Contenido

1. [Punto 1: Configuración de Jenkins en Docker Compose](#punto-1-configuración-de-jenkins-en-docker-compose)
2. [Punto 2: Primer Jenkinsfile – Build, Test y Reportes](#punto-2-primer-jenkinsfile--build-test-y-reportes)
3. [Punto 3: Calidad de Código con SonarQube](#punto-3-calidad-de-código-con-sonarqube)
4. [Punto 4: Empaquetado Docker y Pruebas E2E](#punto-4-empaquetado-docker-y-pruebas-e2e)
5. [Punto 5: Reproducibilidad y Documentación](#punto-5-reproducibilidad-y-documentación)

---

## Punto 1: Configuración de Jenkins en Docker Compose

### Decisión 1.1: Docker Socket Mount vs Docker-in-Docker (DinD)

**Decisión tomada:** Docker Socket Mount

**Justificación:**
- **Simplicidad**: Montar `/var/run/docker.sock` del host permite a Jenkins usar el Docker daemon del host directamente
- **Rendimiento**: No hay overhead de correr un daemon Docker dentro de otro contenedor
- **Aceptable para entorno académico**: Aunque compartir el socket es un riesgo de seguridad (el contenedor tiene control total sobre Docker), es la opción más simple para un proyecto educativo

**Implementación:**
```yaml
# docker-compose.ci.yml
volumes:
  - /var/run/docker.sock:/var/run/docker.sock
user: root  # Necesario para acceder al socket
```

**Alternativa descartada:** Docker-in-Docker requeriría configuración más compleja (privileged mode, volumen para /var/lib/docker) sin beneficio significativo en este contexto.

---

### Decisión 1.2: Plugins Pre-instalados vs Instalación Manual

**Decisión tomada:** Pre-instalación en Dockerfile con `jenkins-plugin-cli`

**Justificación:**
- **Reproducibilidad**: Cada vez que se levanta Jenkins, tiene exactamente los mismos plugins
- **Sin Setup Wizard**: Eliminamos pasos manuales usando `JAVA_OPTS=-Djenkins.install.runSetupWizard=false`
- **Documentación como código**: La lista de plugins está versionada en Git

**Implementación:**
```dockerfile
# jenkins/Dockerfile
RUN jenkins-plugin-cli --plugins \
    workflow-aggregator \
    docker-workflow \
    git \
    junit \
    coverage \
    cucumber-reports \
    htmlpublisher \
    sonar \
    configuration-as-code \
    job-dsl
```

**Plugins críticos instalados desde el inicio:**
- `junit`, `coverage`, `cucumber-reports`, `htmlpublisher`: Publicación de reportes (Reto 6)
- `sonar`: Integración con SonarQube (Punto 3)
- `configuration-as-code`, `job-dsl`: Aprovisionamiento automático

---

### Decisión 1.3: Herramientas en el Contenedor Jenkins vs Agentes Docker

**Decisión tomada:** Instalar herramientas directamente en el contenedor Jenkins

**Justificación:**
- **Complejidad**: Los tests de integración con Testcontainers requieren acceso al socket Docker
- **Testcontainers**: Necesita poder levantar contenedores desde dentro del pipeline
- **Más simple**: Evita configurar agentes Docker y problemas de networking

**Herramientas instaladas:**
```dockerfile
# Maven 3.9.9 (Java builds)
RUN curl -fsSL https://archive.apache.org/dist/maven/...

# Node.js 22 (E2E tests)
RUN curl -fsSL https://deb.nodesource.com/setup_22.x | bash -

# Python 3.11 + pip (Python builds)
RUN apt-get install -y python3 python3-venv python3-pip

# SonarScanner CLI (análisis de Python/Node.js)
RUN curl -fsSL https://binaries.sonarsource.com/.../sonar-scanner-cli-...
```

**Alternativa descartada:** Usar `agent { docker { image 'maven:3.9' } }` habría sido más limpio pero complicaría Testcontainers (DooD - Docker-outside-of-Docker).

---

### Decisión 1.4: Aprovisionamiento de Jobs: JCasC vs init.groovy.d

**Decisión tomada:** Jenkins Configuration as Code (JCasC)

**Justificación:**
- **Declarativo**: YAML es más legible que Groovy
- **Estándar**: JCasC es el método oficial recomendado por Jenkins
- **Validación**: El plugin valida la sintaxis al arrancar

**Implementación:**
```yaml
# jenkins/casc.yaml
jobs:
  - script: |
      pipelineJob('gestion-empleados') {
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

**Variables de entorno usadas:**
- `REPO_URL`: URL del repositorio Git (configurado en `.env.infra`)
- `REPO_BRANCH`: Rama a monitorear (default: main)

---

### Decisión 1.5: Executor Único vs Múltiples Executors

**Decisión tomada:** Un solo executor (`numExecutors: 1`)

**Justificación:**
- **Conflictos de puertos**: Ambos pipelines levantan el sistema completo en el puerto 8080 durante E2E
- **Recursos compartidos**: Docker Compose usa el mismo nombre de proyecto para ambos
- **Secuencial es seguro**: Sacrificamos paralelismo para evitar fallos por colisiones

**Configuración:**
```yaml
# jenkins/casc.yaml
jenkins:
  numExecutors: 1  # Los dos pipelines ejecutan E2E con el mismo compose/puertos
```

**Mejora futura:** Usar puertos dinámicos o nombres de proyecto únicos por build permitiría paralelizar.

---

### Decisión 1.6: Setup Wizard Desactivado

**Decisión tomada:** Desactivar el asistente inicial

**Justificación:**
- Los plugins ya están pre-instalados
- El usuario admin se crea automáticamente vía JCasC
- Los jobs están configurados automáticamente
- No hay pasos manuales necesarios

**Implementación:**
```yaml
# docker-compose.ci.yml
environment:
  JAVA_OPTS: "-Djenkins.install.runSetupWizard=false ..."
```

---

### Decisión 1.7: Testcontainers en Jenkins

**Decisión tomada:** Configurar Testcontainers con Ryuk deshabilitado

**Justificación:**
- **Ryuk**: Contenedor de limpieza automática que puede fallar en CI
- **Reuse disabled**: Evita reutilizar contenedores entre builds
- **Unix socket**: Forzamos el uso del socket montado

**Configuración:**
```dockerfile
# jenkins/Dockerfile
RUN mkdir -p /root/.testcontainers \
 && echo "testcontainers.reuse.enable=false" > /root/.testcontainers/.testcontainers.properties \
 && echo "ryuk.disabled=true" >> /root/.testcontainers/.testcontainers.properties
```

**Variables de entorno en pipeline:**
```groovy
environment {
    TESTCONTAINERS_RYUK_DISABLED = 'true'
    DOCKER_HOST = 'unix:///var/run/docker.sock'
}
```

---

## Punto 2: Primer Jenkinsfile – Build, Test y Reportes

### Decisión 2.1: Pipeline Declarativo vs Scripted

**Decisión tomada:** Pipeline Declarativo

**Justificación:**
- **Legibilidad**: Estructura clara con stages/steps
- **Validación**: Jenkins valida la sintaxis automáticamente
- **Estándar**: Es el método recomendado por Jenkins
- **Suficiente**: Cubre todos los casos del reto sin necesitar Groovy avanzado

**Estructura base:**
```groovy
pipeline {
    agent any
    options { timestamps(); disableConcurrentBuilds(); timeout(time: 45, unit: 'MINUTES') }
    environment { ... }
    stages { ... }
    post { ... }
}
```

---

### Decisión 2.2: Fallido vs Inestable (FAILURE vs UNSTABLE)

**Decisión tomada:** Distinción clara entre estados

**Política implementada:**
- **FAILURE** (rojo): No compila, no se conecta a DB, Quality Gate falla
- **UNSTABLE** (amarillo): Compiló pero hay pruebas/escenarios fallando

**Justificación:**
- El build UNSTABLE permite que los reportes se publiquen
- Distingue entre "el pipeline murió" vs "el pipeline completó pero hay fallos de negocio"
- Los escenarios E2E fallidos no deberían bloquear la visualización del reporte Cucumber

**Implementación:**
```groovy
stage('Test') {
    steps {
        sh 'mvn verify -Dmaven.test.failure.ignore=true'
    }
    post {
        always {
            junit '**/*.xml'
            script {
                if (currentBuild.result == 'UNSTABLE') {
                    error('Hay pruebas fallidas: revise la pestaña "Test Result"')
                }
            }
        }
    }
}
```

**Justificación de `-Dmaven.test.failure.ignore=true`:**
- Sin este flag, Maven aborta inmediatamente cuando falla una prueba
- El pipeline nunca llegaría al bloque `post` para publicar el reporte
- El patrón correcto: dejar que las pruebas terminen, publicar el reporte, y que `junit` marque el build como UNSTABLE

---

### Decisión 2.3: Publicación de Reportes - Plugins Usados

**Decisión tomada:** Tres plugins complementarios

| Plugin | Qué hace | Por qué |
|--------|----------|---------|
| `junit` | Lee JUnit XML, marca el build UNSTABLE si hay fallos | Máquina-legible, genera tendencias |
| `recordCoverage` | Lee JaCoCo/Cobertura XML, muestra % de cobertura | Sustituye al antiguo plugin jacoco, soporta múltiples formatos |
| `cucumber` o `publishHTML` | Muestra escenarios BDD navegables | Documentación viva en lenguaje de negocio |

**Implementación:**
```groovy
post {
    always {
        // 1. Resultados unitarios/integración
        junit testResults: "${env.SVC_DIR}/target/surefire-reports/*.xml"
        
        // 2. Cobertura
        recordCoverage(
            tools: [[parser: 'JACOCO', pattern: "${env.SVC_DIR}/target/site/jacoco/jacoco.xml"]],
            sourceCodeRetention: 'EVERY_BUILD'
        )
    }
}
```

**Por qué tres plugins y no uno:**
- Cada uno tiene un propósito específico
- `junit` es esencial para marcar el estado del build
- `recordCoverage` genera gráficas de tendencia de cobertura
- `cucumber` muestra los escenarios en español con su estado

---

### Decisión 2.4: Tests de Integración con Testcontainers

**Decisión tomada:** Excluir tests de integración de la etapa Test

**Problema identificado:**
- Los tests de integración con Testcontainers levantaban Postgres durante la etapa Test
- Esto duplicaba el esfuerzo: Test con Testcontainers + E2E con el sistema completo
- Los tests de integración ya se verifican en el E2E real

**Implementación:**
```groovy
stage('Test') {
    steps {
        // Solo unitarias (excluye *Integration*)
        sh 'mvn verify -Dtest=!*Integration*'
    }
}
```

**Alternativa descartada:** Correr todo en Test habría funcionado pero ralentizaba el pipeline sin valor adicional (E2E ya valida la integración).

---

### Decisión 2.5: Dependencias en Caché

**Decisión tomada:** Volumen persistente para cachés

**Problema:**
- Sin caché, cada build descarga todas las dependencias Maven/npm/pip desde cero
- Esto puede tomar 5-10 minutos adicionales por build

**Solución:**
```yaml
# docker-compose.ci.yml
volumes:
  - jenkins_data:/var/jenkins_home  # incluye ~/.m2, ~/.npm y pip
```

**Cómo funciona:**
- Maven guarda dependencias en `/var/jenkins_home/.m2/repository`
- npm las guarda en `/var/jenkins_home/.npm`
- pip las guarda en `/var/jenkins_home/.cache/pip`
- Como `/var/jenkins_home` es un volumen, las dependencias sobreviven entre builds

---

### Decisión 2.6: Checkout Automático

**Decisión tomada:** No usar `git url:...` explícito

**Justificación:**
- El Jenkinsfile está en el mismo repositorio que el código
- Jenkins hace checkout automático cuando el job está configurado como Pipeline from SCM
- `checkout scm` es redundante pero explícito (ayuda a la legibilidad)

**Implementación:**
```groovy
stage('Checkout') {
    steps { checkout scm }
}
```

---

## Punto 3: Calidad de Código con SonarQube

### Decisión 3.1: SonarQube Community vs Developer

**Decisión tomada:** SonarQube LTS Community Edition

**Justificación:**
- **Gratuito**: No requiere licencia
- **Suficiente**: Analiza Java, Python, JavaScript, Go, C#
- **Multi-lenguaje**: Un solo dashboard para todos los servicios
- **Quality Gates**: Soporta umbrales personalizados

**Imagen usada:**
```yaml
services:
  sonarqube:
    image: sonarqube:lts-community
```

---

### Decisión 3.2: Quality Gate Personalizado vs "Sonar way"

**Decisión tomada:** Quality Gate personalizado con cobertura ≥ 70%

**Justificación:**
- El proyecto final requiere 70% de cobertura mínimo
- El Quality Gate por defecto ("Sonar way") requiere 80% en código nuevo
- Necesitamos un umbral consistente con los requisitos del proyecto

**Configuración manual en SonarQube:**
1. Administration → Quality Gates → Create
2. Nombre: "Reto 7 - 70%"
3. Condición: `Coverage on New Code < 70%` → FAILED
4. Asignar a los proyectos gestion-empleados y gestion-departamentos

---

### Decisión 3.3: Token de SonarQube en Jenkins Credentials

**Decisión tomada:** Almacenar token como Secret Text en Jenkins

**Justificación:**
- **Seguridad**: No hardcodear el token en el Jenkinsfile
- **Rotación**: Fácil cambiar el token sin modificar código
- **Best practice**: Jenkins Credentials es el mecanismo estándar

**Implementación:**
```yaml
# jenkins/casc.yaml
credentials:
  system:
    domainCredentials:
      - credentials:
          - string:
              id: "sonar-token"
              secret: "${SONAR_TOKEN:-pendiente}"
```

```groovy
// Jenkinsfile
withSonarQubeEnv('sonarqube') {  // Inyecta SONAR_HOST_URL y SONAR_AUTH_TOKEN
    sh 'mvn sonar:sonar -Dsonar.token="$SONAR_AUTH_TOKEN"'
}
```

**Alternativa descartada:** Pasar el token en docker-compose.ci.yml lo expondría en variables de entorno visibles en `docker inspect`.

---

### Decisión 3.4: Análisis Java con Plugin vs SonarScanner

**Decisión tomada:** Sonar Maven Plugin

**Justificación:**
- Maven ya está configurado en el proyecto
- El plugin `sonar-maven-plugin` está incluido en Spring Boot parent POM
- Simplifica la configuración (no necesita sonar-project.properties)
- Automáticamente detecta el reporte de JaCoCo generado por `mvn verify`

**Implementación:**
```groovy
stage('SonarQube') {
    steps {
        withSonarQubeEnv('sonarqube') {
            sh '''
                mvn org.sonarsource.scanner.maven:sonar-maven-plugin:sonar \
                  -Dsonar.projectKey=gestion-empleados \
                  -Dsonar.projectName=gestion-empleados
            '''
        }
    }
}
```

---

### Decisión 3.5: Análisis Python con SonarScanner CLI

**Decisión tomada:** SonarScanner instalado en Jenkins

**Justificación:**
- Python no tiene plugin Maven/Gradle
- SonarScanner CLI es el método oficial para lenguajes no-JVM
- Requiere `sonar-project.properties` con configuración del proyecto

**Implementación:**
```dockerfile
# jenkins/Dockerfile
RUN curl -fsSL https://binaries.sonarsource.com/.../sonar-scanner-cli-6.2.1.4610-linux-x64.zip \
 && ln -s /opt/sonar-scanner-6.2.1.4610-linux-x64/bin/sonar-scanner /usr/local/bin/sonar-scanner
```

```properties
# microservicios/gestion-departamentos/sonar-project.properties
sonar.projectKey=gestion-departamentos
sonar.sources=app
sonar.tests=test
sonar.python.coverage.reportPaths=coverage.xml
```

---

### Decisión 3.6: Webhook SonarQube → Jenkins

**Decisión tomada:** Webhook configurado manualmente en SonarQube

**Justificación:**
- `waitForQualityGate` requiere que SonarQube notifique a Jenkins cuando termina el análisis
- No se puede automatizar completamente vía JCasC (requiere credenciales del webhook)

**Configuración manual:**
1. SonarQube: Administration → Configuration → Webhooks → Create
2. Name: Jenkins
3. URL: `http://jenkins:8080/sonarqube-webhook/`
4. Secret: (vacío - red interna)

**Variable necesaria:**
```yaml
# docker-compose.ci.yml
environment:
  SONAR_VALIDATEWEBHOOKS: "false"  # Permite webhooks a IPs privadas
```

---

### Decisión 3.7: SonarQube No Ejecuta las Pruebas

**Importante:** SonarQube **solo importa** los reportes de cobertura generados previamente

**Proceso correcto:**
1. Etapa Test: `mvn verify` → genera `target/site/jacoco/jacoco.xml`
2. Etapa SonarQube: `mvn sonar:sonar` → **lee** `jacoco.xml` y lo sube a SonarQube
3. Quality Gate: Evalúa si el % de cobertura cumple el umbral

**Error común:** Pensar que SonarQube ejecuta las pruebas. No lo hace.

---

## Punto 4: Empaquetado Docker y Pruebas E2E

### Decisión 4.1: Naming Convention de Imágenes Docker

**Decisión tomada:** `proyecto-final/SERVICIO:BUILD_NUMBER-GIT_SHA` + `latest`

**Justificación:**
- **Trazabilidad**: El tag incluye el número de build y el hash del commit
- **Identificación**: Fácil correlacionar la imagen con el build de Jenkins
- **Latest**: Tag adicional para referencia rápida

**Implementación:**
```groovy
stage('Package') {
    steps {
        script {
            def sha = (env.GIT_COMMIT ?: 'local').take(7)
            env.IMAGE_TAG = "${env.BUILD_NUMBER}-${sha}"
        }
        sh 'docker build -t "$IMAGE:$IMAGE_TAG" -t "$IMAGE:latest" .'
    }
}
```

**Ejemplo:** `proyecto-final/gestion-empleados:42-a3f7c12`

---

### Decisión 4.2: Docker Registry Local vs DockerHub

**Decisión tomada:** Docker Registry 2 local

**Justificación:**
- **Simplicidad**: No requiere cuenta externa
- **Velocidad**: Push/pull en localhost es más rápido que internet
- **Sin límites**: DockerHub tiene rate limits para pulls anónimos

**Implementación:**
```yaml
# docker-compose.ci.yml
services:
  registry:
    image: registry:2
    ports:
      - "5000:5000"  # En macOS cambiar a 5001:5000 (AirPlay usa el 5000)
    volumes:
      - registry_data:/var/lib/registry
```

**Push de imágenes:**
```groovy
stage('Publish') {
    steps {
        sh '''
            docker tag "$IMAGE:$IMAGE_TAG" "localhost:5000/$IMAGE:$IMAGE_TAG"
            docker push "localhost:5000/$IMAGE:$IMAGE_TAG"
        '''
    }
}
```

---

### Decisión 4.3: Multi-Stage Builds

**Decisión tomada:** Dockerfiles usan multi-stage builds

**Justificación:**
- **Tamaño**: La imagen final no incluye herramientas de build (Maven, compiladores)
- **Seguridad**: Reduce la superficie de ataque
- **Ya implementados**: Los Dockerfiles del proyecto ya usaban esta técnica desde retos anteriores

**Ejemplo (gestion-empleados):**
```dockerfile
# Stage 1: Build
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src ./src
RUN mvn package -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
CMD ["java", "-jar", "app.jar"]
```

---

### Decisión 4.4: E2E en Pipeline vs Manual

**Decisión tomada:** E2E automatizado en cada build

**Justificación:**
- **Detección temprana**: Un cambio que rompe un escenario se detecta antes del merge
- **Documentación viva**: El reporte Cucumber actualizado en cada build
- **Confianza**: No se puede hacer merge sin que los escenarios pasen

**Costo:**
- Los E2E son la etapa más lenta del pipeline (2-3 minutos)
- Alternativa futura: Solo correr E2E en PRs y en main, no en cada push a feature branches

---

### Decisión 4.5: Compose Project Aislado para E2E

**Decisión tomada:** Proyecto Docker Compose separado con nombre único

**Justificación:**
- **Aislamiento**: El pipeline E2E no afecta la infraestructura CI (Jenkins, SonarQube)
- **Limpieza**: `docker compose down -v` solo borra contenedores/volúmenes del E2E
- **Reproducibilidad**: Cada build parte de un estado limpio

**Implementación:**
```bash
# scripts/ci-e2e-up.sh
P="${COMPOSE_PROJECT:-reto7-e2e}"
docker compose -p "$P" -f docker-compose.yml -f docker-compose.e2e.yml up -d
```

**docker-compose.e2e.yml:** Override específico para CI (ej: RabbitMQ sin volumen persistente para evitar corrupción entre builds).

---

### Decisión 4.6: Health Checks antes de E2E

**Decisión tomada:** Script de espera activa `wait-for-healthy.sh`

**Justificación:**
- **Evita falsos negativos**: Si el E2E corre antes de que el sistema esté listo, falla incorrectamente
- **Timeout**: Falla después de 5 minutos si el sistema no levanta

**Implementación:**
```bash
# scripts/wait-for-healthy.sh
while [ $(docker compose ps --filter health=healthy --filter health=starting --format json | jq -s length) -lt $TOTAL_SERVICES ]; do
    sleep 5
    if [ $ELAPSED_SECONDS -gt $MAX_WAIT_SECONDS ]; then
        echo "⏰ Timeout esperando servicios sanos"
        exit 1
    fi
done
```

---

### Decisión 4.7: Jenkins se Conecta a la Red de la App

**Decisión tomada:** `docker network connect` para que Jenkins alcance `http://api-gateway:8080`

**Justificación:**
- El pipeline corre dentro del contenedor Jenkins
- Los tests E2E necesitan hacer requests HTTP al sistema
- Sin conectar las redes, Jenkins no puede resolver `api-gateway`

**Implementación:**
```bash
# scripts/ci-e2e-up.sh
docker network connect "${P}_microservices-network" jenkins 2>/dev/null || true
```

**Limpieza:**
```bash
# scripts/ci-e2e-down.sh
docker network disconnect "${P}_microservices-network" "$(hostname)" 2>/dev/null || true
docker compose -p "$P" down -v --remove-orphans
```

---

### Decisión 4.8: Publicación Múltiple de Reportes E2E

**Decisión tomada:** Tres plugins para diferentes propósitos

| Plugin | Qué muestra | Por qué |
|--------|-------------|---------|
| `junit` | Conteo de escenarios, estado UNSTABLE | Marca el build, genera tendencias |
| `cucumber` | Características y escenarios navegables | Documentación viva en español |
| `publishHTML` | Reporte HTML estático | Alternativa visual, descargable |
| `archiveArtifacts` | JSON, HTML, logs de Docker | Diagnóstico de fallos |

**Implementación:**
```groovy
post {
    always {
        junit testResults: 'e2e-tests/reports/junit.xml'
        cucumber(
            jsonReportDirectory: 'e2e-tests/reports',
            fileIncludePattern: 'cucumber.json',
            buildStatus: 'UNSTABLE'  // Escenario fallido → build inestable
        )
        publishHTML(target: [
            reportDir: 'e2e-tests/reports',
            reportFiles: 'cucumber.html',
            reportName: 'Reporte BDD (HTML)'
        ])
        archiveArtifacts artifacts: 'e2e-tests/reports/**', allowEmptyArchive: true
    }
    cleanup {
        sh 'bash scripts/ci-e2e-down.sh'
        archiveArtifacts artifacts: 'docker-logs.txt', allowEmptyArchive: true
    }
}
```

**Justificación de archivar `docker-logs.txt`:**
- Cuando un E2E falla, lo primero que se necesita son los logs del sistema
- Sin esto, diagnosticar es imposible (el pipeline ya hizo `docker compose down`)

---

### Decisión 4.9: || true en el Comando de Tests

**Decisión tomada:** `npx cucumber-js || true`

**Justificación:**
- Si el comando falla (escenarios rojos), el pipeline abortaría antes de llegar al `post`
- El patrón correcto: dejar que los tests terminen, publicar el reporte, y que `junit`/`cucumber` marquen el build como UNSTABLE
- Similar a `-Dmaven.test.failure.ignore=true` en Java

**Implementación:**
```groovy
stage('E2E Tests') {
    steps {
        sh 'npx cucumber-js || true'
    }
    post {
        always {
            junit 'reports/junit.xml'  // Aquí se decide el estado del build
        }
    }
}
```

---

### Decisión 4.10: Limpieza con down -v

**Decisión tomada:** Siempre correr `docker compose down -v` después del E2E

**Justificación:**
- **Idempotencia**: Cada build parte de bases de datos vacías
- **Evita contaminación**: Los datos del build 42 no afectan al build 43
- **Evita corrupción de RabbitMQ**: Volúmenes de RabbitMQ pueden corromperse entre builds

**Implementación:**
```groovy
post {
    cleanup {
        sh 'bash scripts/ci-e2e-down.sh'  // Incluye 'down -v'
    }
}
```

---

## Punto 5: Reproducibilidad y Documentación

### Decisión 5.1: Pipeline Idempotente

**Decisión tomada:** El pipeline debe producir el mismo resultado en builds consecutivos

**Cómo se logró:**
1. **Limpieza previa**: `down -v` antes de `up` en E2E
2. **Sin caché de capas Docker**: `--build` fuerza rebuild
3. **Tests determinísticos**: Fixtures con datos predecibles
4. **Sin estado compartido**: Cada build usa su propio proyecto Docker Compose

**Validación:**
- Se corrieron 3 builds consecutivos del mismo commit
- Los tres pasaron con el mismo resultado

---

### Decisión 5.2: Simulación de Fallos

**Decisión tomada:** Probar los 4 tipos de fallo del reto

**Fallos probados:**
1. **Prueba unitaria fallida** → FAILURE en Test
2. **Cobertura < 70%** → FAILURE en Quality Gate
3. **Dockerfile con error** → FAILURE en Package
4. **Escenario E2E fallido** → UNSTABLE en E2E Tests

**Validación:**
- En cada caso, el reporte publicado identificó claramente QUÉ falló
- No solo "FAILED" en el log, sino el nombre de la prueba/escenario en la UI

---

### Decisión 5.3: Documentación en README.md

**Decisión tomada:** README actualizado con sección completa del Reto 7

**Contenido incluido:**
- **Acceso a Jenkins**: URL, credenciales
- **Instrucciones de configuración**: Pasos para levantar infraestructura CI
- **Tabla de reportes**: Qué reportes genera cada etapa y dónde verlos
- **Interpretación de resultados**: Qué significa cada estado del build
- **Capturas de pantalla**: Pipeline exitoso, reportes de pruebas, cobertura, BDD

---

### Decisión 5.4: Triggers: pollSCM vs Webhook

**Decisión tomada:** pollSCM cada 5 minutos

**Justificación:**
- **Simplicidad**: No requiere configurar webhooks en GitHub/GitLab
- **Suficiente para académico**: Builds en máximo 5 minutos después del push
- **Sin exponer Jenkins**: No necesita URL pública

**Implementación:**
```groovy
triggers { pollSCM('H/5 * * * *') }
```

**Alternativa futura:** Webhook real con Jenkins Generic Webhook Trigger Plugin para builds instantáneos.

---

### Decisión 5.5: Separación docker-compose.yml vs docker-compose.ci.yml

**Decisión tomada:** Infraestructura CI en archivo separado

**Justificación:**
- **Independencia**: El pipeline hace `down -v` de la app, pero no debe apagar Jenkins
- **Claridad**: La app y la infraestructura CI son conceptos separados
- **Diferentes .env**: La app usa `.env`, la CI usa `.env.infra`

**Estructura:**
```
docker-compose.yml          # Aplicación (servicios de negocio)
docker-compose.ci.yml       # Infraestructura CI (Jenkins, SonarQube, Registry)
docker-compose.e2e.yml      # Overrides para E2E
```

---

## 🎯 Resumen de Decisiones Clave

| Aspecto | Decisión | Alternativa Descartada | Justificación |
|---------|----------|------------------------|---------------|
| Docker | Socket Mount | Docker-in-Docker | Simplicidad y rendimiento |
| Plugins | Pre-instalados en Dockerfile | Instalación manual | Reproducibilidad |
| Herramientas | En contenedor Jenkins | Agentes Docker | Testcontainers necesita socket |
| Jobs | JCasC | init.groovy.d | Declarativo y estándar |
| Executors | 1 | Múltiples | Evita colisiones de puertos en E2E |
| Pipeline | Declarativo | Scripted | Legibilidad |
| Estados | FAILURE vs UNSTABLE | Solo FAILURE | Distingue "no compila" vs "pruebas fallan" |
| Reportes | junit + coverage + cucumber | Solo logs | Visibilidad en UI |
| SonarQube | Community | Developer | Gratuito y suficiente |
| Quality Gate | Personalizado 70% | Sonar way 80% | Requisito del proyecto |
| Token | Jenkins Credentials | Hardcoded | Seguridad |
| Análisis Java | Sonar Maven Plugin | SonarScanner | Integración con Maven |
| Análisis Python | SonarScanner CLI | Manual | Método oficial para Python |
| Imágenes | BUILD_NUMBER-GIT_SHA | Solo latest | Trazabilidad |
| Registry | Local | DockerHub | Simplicidad y velocidad |
| E2E | En pipeline | Manual | Detección temprana |
| Compose E2E | Proyecto separado | Mismo que app | Aislamiento y limpieza |
| Health checks | wait-for-healthy.sh | depends_on | Garantiza que sistema esté listo |
| Limpieza | down -v después de E2E | Sin limpieza | Idempotencia |
| Triggers | pollSCM | Webhook | Simplicidad |
| Infraestructura | docker-compose.ci.yml separado | Todo en uno | Independencia |

---

## 📚 Referencias

- [Jenkins Pipeline Syntax](https://www.jenkins.io/doc/book/pipeline/syntax/)
- [Jenkins Configuration as Code](https://github.com/jenkinsci/configuration-as-code-plugin)
- [SonarQube Documentation](https://docs.sonarqube.org/)
- [Docker Registry Documentation](https://docs.docker.com/registry/)
- [Testcontainers Documentation](https://www.testcontainers.org/)

---

## 🔍 Troubleshooting Común

### Problema: Jenkins no puede construir imágenes Docker

**Síntoma:** `docker: command not found` en el pipeline

**Causa:** Docker CLI no está instalado en el contenedor Jenkins

**Solución:** Verificar que el Dockerfile de Jenkins incluye:
```dockerfile
RUN apt-get install -y docker-ce-cli
```

---

### Problema: Testcontainers no puede levantar contenedores

**Síntoma:** `Could not find a valid Docker environment`

**Causa:** Testcontainers no puede acceder al socket Docker

**Solución:** Verificar:
1. Socket montado: `-v /var/run/docker.sock:/var/run/docker.sock`
2. Usuario root: `user: root` en docker-compose.ci.yml
3. Variables de entorno: `TESTCONTAINERS_RYUK_DISABLED=true`

---

### Problema: E2E falla con "Connection refused" a api-gateway

**Síntoma:** Los tests E2E no pueden hacer requests HTTP

**Causa:** Jenkins no está en la misma red que la app

**Solución:** Verificar que `ci-e2e-up.sh` ejecuta:
```bash
docker network connect "${P}_microservices-network" jenkins
```

---

### Problema: SonarQube muestra cobertura 0%

**Síntoma:** Quality Gate falla porque cobertura es 0%

**Causa:** SonarQube no encuentra el reporte de cobertura

**Solución:** Verificar:
1. La etapa Test generó el reporte: `target/site/jacoco/jacoco.xml`
2. El path en sonar-project.properties coincide: `sonar.python.coverage.reportPaths=coverage.xml`
3. La etapa SonarQube corre DESPUÉS de Test

---

### Problema: Webhook de SonarQube a Jenkins falla

**Síntoma:** `waitForQualityGate` se queda esperando forever

**Causa:** El webhook no está configurado o falla

**Solución:**
1. Verificar webhook en SonarQube: Administration → Webhooks
2. URL debe ser: `http://jenkins:8080/sonarqube-webhook/`
3. Verificar `SONAR_VALIDATEWEBHOOKS=false` en docker-compose.ci.yml

---

### Problema: RabbitMQ falla en E2E con "corrupt database"

**Síntoma:** E2E falla porque RabbitMQ no levanta

**Causa:** Volumen de RabbitMQ corrupto de build anterior

**Solución:** El script `ci-e2e-up.sh` ya incluye limpieza forzada:
```bash
docker volume rm "${P}_rabbitmq-data" 2>/dev/null || true
```

---

## ✅ Checklist de Validación

- [ ] Jenkins accesible en http://localhost:9090
- [ ] SonarQube accesible en http://localhost:9000
- [ ] Registry accesible: `curl http://localhost:5000/v2/_catalog`
- [ ] 2 pipelines configurados automáticamente: gestion-empleados y gestion-departamentos
- [ ] Pipeline ejecuta: Checkout → Build → Test → SonarQube → Quality Gate → Package → Publish → E2E
- [ ] Reportes visibles: Test Result, Coverage, Cucumber Reports
- [ ] Build UNSTABLE cuando falla una prueba (no FAILURE)
- [ ] Build FAILURE cuando falla Quality Gate
- [ ] Logs de Docker archivados en artifacts
- [ ] Pipeline idempotente (3 builds consecutivos con mismo resultado)
- [ ] Simulación de fallos validada (unitarias, cobertura, Dockerfile, E2E)
- [ ] README documentado con instrucciones y capturas

---

## 🚀 Próximos Pasos (Mejoras Futuras)

1. **Paralelizar pipelines**: Usar puertos dinámicos para permitir builds concurrentes
2. **Webhook real**: Reemplazar pollSCM con webhook de GitHub/GitLab
3. **Notificaciones**: Enviar notificaciones a Slack/email cuando falla el build
4. **Caché de capas Docker**: Usar BuildKit con caché para acelerar builds
5. **Análisis de seguridad**: Agregar Trivy o Snyk para escanear imágenes Docker
6. **Métricas**: Integrar con Prometheus/Grafana para monitorear tiempos de build
7. **Blue Ocean**: Usar plugin Blue Ocean para visualización moderna del pipeline
8. **Multibranch Pipeline**: Detectar automáticamente feature branches y crear pipelines
9. **Deployment**: Agregar etapa de deploy a entorno de staging/producción
10. **Rollback automático**: Si el E2E falla en producción, hacer rollback automático

