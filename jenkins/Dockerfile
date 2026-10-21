FROM jenkins/jenkins:lts-jdk21

# 1) Plugins pre-instalados (reproducible, sin pasos manuales en la UI)
#    Nota: "Docker Pipeline" se llama docker-workflow (no existe el id docker-pipeline).
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

# 2) Herramientas dentro del contenedor Jenkins (Opción A: sin agentes Docker,
#    necesaria para que Testcontainers use el socket sin trucos de rutas)
USER root

RUN apt-get update \
 && apt-get install -y --no-install-recommends ca-certificates curl gnupg git unzip \
      python3 python3-venv python3-pip \
 && install -m 0755 -d /etc/apt/keyrings \
 && curl -fsSL https://download.docker.com/linux/debian/gpg -o /etc/apt/keyrings/docker.asc \
 && chmod a+r /etc/apt/keyrings/docker.asc \
 && echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/debian $(. /etc/os-release && echo $VERSION_CODENAME) stable" \
      > /etc/apt/sources.list.d/docker.list \
 && curl -fsSL https://deb.nodesource.com/setup_22.x | bash - \
 && apt-get install -y --no-install-recommends docker-ce-cli docker-compose-plugin nodejs \
 && rm -rf /var/lib/apt/lists/*

ARG MAVEN_VERSION=3.9.9
RUN curl -fsSL https://archive.apache.org/dist/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz \
    | tar -xz -C /opt \
 && ln -s /opt/apache-maven-${MAVEN_VERSION}/bin/mvn /usr/local/bin/mvn

# Instalar SonarScanner CLI para análisis de proyectos Python/Node.js
ARG SONAR_SCANNER_VERSION=6.2.1.4610
RUN curl -fsSL https://binaries.sonarsource.com/Distribution/sonar-scanner-cli/sonar-scanner-cli-${SONAR_SCANNER_VERSION}-linux-x64.zip -o /tmp/sonar-scanner.zip \
 && unzip -q /tmp/sonar-scanner.zip -d /opt \
 && rm /tmp/sonar-scanner.zip \
 && ln -s /opt/sonar-scanner-${SONAR_SCANNER_VERSION}-linux-x64/bin/sonar-scanner /usr/local/bin/sonar-scanner \
 && chmod +x /usr/local/bin/sonar-scanner

# El compose ejecuta este contenedor como root (socket Docker), ver docker-compose.ci.yml
USER root

# Configuración de Testcontainers para CI
RUN mkdir -p /root/.testcontainers \
 && echo "testcontainers.reuse.enable=false" > /root/.testcontainers/.testcontainers.properties \
 && echo "ryuk.disabled=true" >> /root/.testcontainers/.testcontainers.properties \
 && echo "docker.client.strategy=org.testcontainers.dockerclient.UnixSocketClientProviderStrategy" >> /root/.testcontainers/.testcontainers.properties \
 && chmod 644 /root/.testcontainers/.testcontainers.properties
