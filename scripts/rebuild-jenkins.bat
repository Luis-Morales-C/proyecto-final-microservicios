@echo off
REM Script para reconstruir el contenedor Jenkins con sonar-scanner instalado

echo ====================================
echo Reconstruyendo Jenkins con sonar-scanner
echo ====================================
echo.

echo [1/4] Deteniendo contenedor Jenkins...
docker compose -f docker-compose.ci.yml --env-file .env.infra stop jenkins

echo.
echo [2/4] Reconstruyendo imagen Jenkins...
docker compose -f docker-compose.ci.yml --env-file .env.infra build --no-cache jenkins

echo.
echo [3/4] Iniciando contenedor Jenkins...
docker compose -f docker-compose.ci.yml --env-file .env.infra up -d jenkins

echo.
echo [4/4] Esperando que Jenkins este listo...
timeout /t 30 /nobreak

echo.
echo ====================================
echo Verificando instalacion de herramientas
echo ====================================
docker exec jenkins mvn --version
docker exec jenkins node --version
docker exec jenkins python3 --version
docker exec jenkins sonar-scanner --version

echo.
echo ====================================
echo Jenkins reconstruido exitosamente
echo Accede a http://localhost:9090
echo ====================================
