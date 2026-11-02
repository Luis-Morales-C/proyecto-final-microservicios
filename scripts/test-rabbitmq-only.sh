#!/usr/bin/env bash
# Script para probar SOLO RabbitMQ en aislamiento
set -euo pipefail

P="test-rabbit"

echo "🧹 Limpiando contenedores de prueba anteriores..."
docker compose -p "$P" down -v --remove-orphans 2>/dev/null || true
docker rm -f test-rabbitmq 2>/dev/null || true
docker volume rm "${P}_rabbitmq-data" 2>/dev/null || true

echo "🚀 Creando RabbitMQ de prueba..."
docker run -d \
  --name test-rabbitmq \
  -e RABBITMQ_DEFAULT_USER=admin \
  -e RABBITMQ_DEFAULT_PASS=admin123 \
  -e RABBITMQ_ERLANG_COOKIE=CHANGEME_SECURE_COOKIE \
  -p 15672:15672 \
  -p 5672:5672 \
  rabbitmq:4-management

echo "⏳ Esperando a que RabbitMQ inicie..."
for i in {1..60}; do
    if docker exec test-rabbitmq rabbitmq-diagnostics -q ping 2>/dev/null; then
        echo "✅ RabbitMQ está respondiendo en intento $i"
        break
    fi
    echo "   Intento $i/60: RabbitMQ aún no responde..."
    sleep 2
done

echo ""
echo "📋 Estado del contenedor:"
docker ps --filter name=test-rabbitmq

echo ""
echo "📋 Últimas líneas de logs:"
docker logs test-rabbitmq --tail 20

echo ""
echo "✅ Prueba completada. Para limpiar ejecuta:"
echo "   docker rm -f test-rabbitmq"
