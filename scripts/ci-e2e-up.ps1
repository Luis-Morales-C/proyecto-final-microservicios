# Equivalente PowerShell de ci-e2e-up.sh para testing local en Windows
$ErrorActionPreference = "Stop"
$P = if ($env:COMPOSE_PROJECT) { $env:COMPOSE_PROJECT } else { "reto7-e2e" }

Write-Host "📋 Usando project name: $P" -ForegroundColor Cyan

Copy-Item -Force .env.ci .env

Write-Host "🧹 Limpiando contenedores del proyecto E2E..." -ForegroundColor Yellow
docker compose -p $P -f docker-compose.yml -f docker-compose.e2e.yml down -v --remove-orphans 2>$null

Write-Host "🧹 Forzando eliminación de contenedores sin prefijo..." -ForegroundColor Yellow
docker rm -f rabbitmq rabbitmq-init 2>$null
docker rm -f database-empleados database-departamentos database-auth database-perfiles database-vacaciones database-notificaciones 2>$null
docker rm -f gestion-empleados gestion-departamentos auth-service perfiles-service vacaciones-service notificaciones-service api-gateway 2>$null

Start-Sleep -Seconds 2

Write-Host "🧹 Forzando eliminación de volúmenes del proyecto..." -ForegroundColor Yellow
docker volume rm "${P}_rabbitmq-data" 2>$null
docker volume rm "${P}_empleados-db-data" 2>$null
docker volume rm "${P}_departamentos-db-data" 2>$null
docker volume rm "${P}_auth-db-data" 2>$null
docker volume rm "${P}_perfiles-db-data" 2>$null
docker volume rm "${P}_vacaciones-db-data" 2>$null
docker volume rm "${P}_notificaciones-db-data" 2>$null

Write-Host "🔨 Construyendo imágenes..." -ForegroundColor Green
docker compose -p $P -f docker-compose.yml -f docker-compose.e2e.yml build

Write-Host "🚀 Levantando servicios..." -ForegroundColor Green
docker compose -p $P -f docker-compose.yml -f docker-compose.e2e.yml up -d --no-build

Write-Host ""
Write-Host "⏳ Esperando a que RabbitMQ inicie..." -ForegroundColor Cyan
Write-Host "   (Abre otra terminal y ejecuta: docker logs -f ${P}-rabbitmq-1)" -ForegroundColor Yellow
Write-Host ""

for ($i = 1; $i -le 60; $i++) {
    $status = docker compose -p $P -f docker-compose.yml -f docker-compose.e2e.yml ps rabbitmq 2>$null | Select-String "healthy"
    if ($status) {
        Write-Host "✅ RabbitMQ está healthy en intento $i" -ForegroundColor Green
        break
    }
    Write-Host "   Intento $i/60: RabbitMQ aún no está ready..." -ForegroundColor Gray
    Start-Sleep -Seconds 5
}

Write-Host ""
Write-Host "📊 Estado de contenedores:" -ForegroundColor Cyan
docker compose -p $P -f docker-compose.yml -f docker-compose.e2e.yml ps

Write-Host ""
Write-Host "📋 Logs de RabbitMQ (últimas 30 líneas):" -ForegroundColor Cyan
$rabbitContainer = docker compose -p $P -f docker-compose.yml -f docker-compose.e2e.yml ps -q rabbitmq 2>$null
if ($rabbitContainer) {
    docker logs $rabbitContainer --tail 30 2>&1
}

Write-Host ""
Write-Host "✅ Servicios levantados. Para ver logs en vivo:" -ForegroundColor Green
Write-Host "   docker logs -f $rabbitContainer" -ForegroundColor White
