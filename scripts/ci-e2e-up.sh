#!/usr/bin/env bash
# Levanta la aplicación completa para el E2E, conecta Jenkins a su red y espera a que esté sana.
set -euo pipefail
P="${COMPOSE_PROJECT:-reto7-e2e}"

cp -f .env.ci .env

# Limpiar TODOS los contenedores del proyecto E2E
echo "🧹 Limpiando contenedores del proyecto E2E..."
docker compose -p "$P" -f docker-compose.yml -f docker-compose.e2e.yml down -v --remove-orphans 2>/dev/null || true

# Forzar eliminación de contenedores específicos que pueden tener nombres sin prefijo
echo "🧹 Forzando eliminación de contenedores sin prefijo..."
docker rm -f rabbitmq rabbitmq-init 2>/dev/null || true
docker rm -f database-empleados database-departamentos database-auth database-perfiles database-vacaciones database-notificaciones 2>/dev/null || true
docker rm -f gestion-empleados gestion-departamentos auth-service perfiles-service vacaciones-service notificaciones-service api-gateway 2>/dev/null || true

# Esperar un poco para asegurar que los volúmenes se desmonten
sleep 2

# Forzar eliminación de volumen de RabbitMQ específicamente (puede estar corrupto)
echo "🧹 Forzando eliminación de volumen RabbitMQ..."
docker volume rm "${P}_rabbitmq-data" 2>/dev/null || true

# Verificar y limpiar otros volúmenes del proyecto E2E
echo "🧹 Limpiando volúmenes restantes del proyecto E2E..."
docker volume rm "${P}_empleados-db-data" 2>/dev/null || true
docker volume rm "${P}_departamentos-db-data" 2>/dev/null || true
docker volume rm "${P}_auth-db-data" 2>/dev/null || true
docker volume rm "${P}_perfiles-db-data" 2>/dev/null || true
docker volume rm "${P}_vacaciones-db-data" 2>/dev/null || true
docker volume rm "${P}_notificaciones-db-data" 2>/dev/null || true

# En CI: construir todas las imágenes aprovechando la caché de capas
# Usar docker-compose.e2e.yml para override de configuración E2E (RabbitMQ sin volumen persistente)
docker compose -p "$P" -f docker-compose.yml -f docker-compose.e2e.yml build
docker compose -p "$P" -f docker-compose.yml -f docker-compose.e2e.yml up -d --no-build

# Esperar un poco y verificar estado de RabbitMQ
sleep 5
echo "📊 Estado de contenedores del proyecto E2E:"
docker compose -p "$P" -f docker-compose.yml -f docker-compose.e2e.yml ps

# Obtener nombre del contenedor rabbitmq con el prefijo del proyecto
RABBITMQ_CONTAINER=$(docker compose -p "$P" -f docker-compose.yml -f docker-compose.e2e.yml ps -q rabbitmq 2>/dev/null || echo "")
if [ -n "$RABBITMQ_CONTAINER" ]; then
    echo "📊 Estado de RabbitMQ ($RABBITMQ_CONTAINER):"
    docker logs "$RABBITMQ_CONTAINER" --tail 50 2>&1 || echo "⚠️ No se pudo obtener logs de RabbitMQ"
fi

# Jenkins se une a la red de la app para alcanzar http://api-gateway:8080
docker network connect "${P}_microservices-network" jenkins 2>/dev/null || true

bash scripts/wait-for-healthy.sh
