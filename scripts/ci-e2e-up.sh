#!/usr/bin/env bash
# Levanta la aplicación completa para el E2E, conecta Jenkins a su red y espera a que esté sana.
set -euo pipefail
P="${COMPOSE_PROJECT:-reto7-e2e}"

cp -f .env.ci .env

# Limpiar TODOS los contenedores del proyecto y contenedores sin prefijo que puedan estar corriendo
echo "🧹 Limpiando contenedores existentes..."
docker compose -p "$P" -f docker-compose.yml down -v --remove-orphans 2>/dev/null || true
docker compose -f docker-compose.yml down -v --remove-orphans 2>/dev/null || true

# Limpiar contenedores específicos que puedan tener nombres sin prefijo
docker rm -f rabbitmq database-empleados database-departamentos database-auth database-perfiles database-vacaciones database-notificaciones gestion-empleados gestion-departamentos api-gateway auth-service perfiles-service vacaciones-service notificaciones-service 2>/dev/null || true

# En CI: Si las imágenes están en el registry, usarlas; si no, hacer build
# Intentar pull primero
if docker pull localhost:5000/proyecto-final/gestion-empleados:latest 2>/dev/null && \
   docker pull localhost:5000/proyecto-final/gestion-departamentos:latest 2>/dev/null; then
    echo "✅ Usando imágenes del registry - etiquetando para docker-compose"
    # Tag para que docker-compose las encuentre con el nombre esperado
    docker tag localhost:5000/proyecto-final/gestion-empleados:latest proyecto-final-microservicios-gestion-empleados
    docker tag localhost:5000/proyecto-final/gestion-departamentos:latest proyecto-final-microservicios-gestion-departamentos
    # Usar las imágenes existentes
    docker compose -p "$P" -f docker-compose.yml up -d --no-build
else
    echo "⚠️ Imágenes no en registry, construyendo con docker-compose"
    docker compose -p "$P" -f docker-compose.yml up -d --build
fi

# Jenkins se une a la red de la app para alcanzar http://api-gateway:8080
docker network connect "${P}_microservices-network" jenkins 2>/dev/null || true

bash scripts/wait-for-healthy.sh
