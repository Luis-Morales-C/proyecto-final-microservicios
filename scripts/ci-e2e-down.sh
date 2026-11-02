#!/usr/bin/env bash
# Guarda logs, desconecta Jenkins de la red de la app (si no, "down" no puede borrar la red) y limpia volúmenes.
P="${COMPOSE_PROJECT:-reto7-e2e}"
docker compose -p "$P" -f docker-compose.yml -f docker-compose.e2e.yml logs --no-color > docker-logs.txt 2>&1 || true
docker network disconnect "${P}_microservices-network" "$(hostname)" 2>/dev/null || true
docker compose -p "$P" -f docker-compose.yml -f docker-compose.e2e.yml down -v --remove-orphans
