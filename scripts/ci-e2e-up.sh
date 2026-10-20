#!/usr/bin/env bash
# Levanta la aplicación completa para el E2E, conecta Jenkins a su red y espera a que esté sana.
set -euo pipefail
P="${COMPOSE_PROJECT:-reto7-e2e}"

cp -f .env.ci .env

# Limpiar TODOS los contenedores del proyecto E2E
echo "🧹 Limpiando contenedores del proyecto E2E..."
docker compose -p "$P" -f docker-compose.yml down -v --remove-orphans 2>/dev/null || true

# Verificar y limpiar volúmenes del proyecto E2E específicamente
echo "🧹 Limpiando volúmenes del proyecto E2E..."
docker volume rm "${P}_rabbitmq-data" 2>/dev/null || true
docker volume rm "${P}_empleados-db-data" 2>/dev/null || true
docker volume rm "${P}_departamentos-db-data" 2>/dev/null || true
docker volume rm "${P}_auth-db-data" 2>/dev/null || true
docker volume rm "${P}_perfiles-db-data" 2>/dev/null || true
docker volume rm "${P}_vacaciones-db-data" 2>/dev/null || true
docker volume rm "${P}_notificaciones-db-data" 2>/dev/null || true

# En CI: construir todas las imágenes aprovechando la caché de capas
docker compose -p "$P" -f docker-compose.yml build
docker compose -p "$P" -f docker-compose.yml up -d --no-build

# Esperar un poco y verificar estado de RabbitMQ
sleep 5
echo "📊 Estado de contenedores del proyecto E2E:"
docker compose -p "$P" ps

# Obtener nombre del contenedor rabbitmq con el prefijo del proyecto
RABBITMQ_CONTAINER=$(docker compose -p "$P" ps -q rabbitmq 2>/dev/null || echo "")
if [ -n "$RABBITMQ_CONTAINER" ]; then
    echo "📊 Estado de RabbitMQ ($RABBITMQ_CONTAINER):"
    docker logs "$RABBITMQ_CONTAINER" --tail 50 2>&1 || echo "⚠️ No se pudo obtener logs de RabbitMQ"
fi

# Jenkins se une a la red de la app para alcanzar http://api-gateway:8080
docker network connect "${P}_microservices-network" jenkins 2>/dev/null || true

bash scripts/wait-for-healthy.sh
