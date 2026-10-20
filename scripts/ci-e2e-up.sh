#!/usr/bin/env bash
# Levanta la aplicación completa para el E2E, conecta Jenkins a su red y espera a que esté sana.
set -euo pipefail
P="${COMPOSE_PROJECT:-reto7-e2e}"

cp -f .env.ci .env
docker compose -p "$P" -f docker-compose.yml down -v --remove-orphans || true
docker compose -p "$P" -f docker-compose.yml up -d --build

# Jenkins se une a la red de la app para alcanzar http://api-gateway:8080
docker network connect "${P}_microservices-network" "$(hostname)" || true

bash scripts/wait-for-healthy.sh
