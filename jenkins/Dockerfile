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
 && apt-get install -y --no-install-recommends ca-certificates curl gnupg git \
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

# El compose ejecuta este contenedor como root (socket Docker), ver docker-compose.ci.yml
USER root
