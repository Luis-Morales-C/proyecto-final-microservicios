#!/usr/bin/env bash
# Espera (polling con timeout) a que: 1) todos los contenedores con healthcheck estén "healthy"
# y 2) respondan los endpoints HTTP indicados.
set -uo pipefail
P="${COMPOSE_PROJECT:-reto7-e2e}"
TIMEOUT="${WAIT_TIMEOUT:-300}"
GATEWAY="${BASE_URL:-http://api-gateway:8080}"
# gestion-empleados no tiene healthcheck: verificar su OpenAPI (configurado en springdoc.api-docs.path)
URLS=("$GATEWAY/health" "http://gestion-empleados:8081/empleados/openapi")

deadline=$(( $(date +%s) + TIMEOUT ))
while true; do
  ok=1; detail=""
  for id in $(docker compose -p "$P" -f docker-compose.yml ps -q); do
    st=$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "$id")
    if [ "$st" != "healthy" ] && [ "$st" != "none" ]; then
      ok=0; detail="$detail $(docker inspect -f '{{.Name}}' "$id")=$st"
    fi
  done
  for u in "${URLS[@]}"; do
    curl -fsS -o /dev/null --max-time 3 "$u" || { ok=0; detail="$detail $u"; }
  done
  if [ "$ok" = 1 ]; then echo "Sistema listo"; exit 0; fi
  if [ "$(date +%s)" -ge "$deadline" ]; then
    echo "TIMEOUT esperando al sistema. Pendiente:$detail" >&2
    docker compose -p "$P" -f docker-compose.yml ps >&2 || true
    exit 1
  fi
  echo "Esperando...$detail"; sleep 5
done
