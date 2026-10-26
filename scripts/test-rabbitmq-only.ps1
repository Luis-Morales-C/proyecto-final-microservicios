# Script para probar SOLO RabbitMQ en aislamiento
$ErrorActionPreference = "Continue"

Write-Host "🧹 Limpiando contenedores de prueba anteriores..." -ForegroundColor Yellow
docker rm -f test-rabbitmq 2>$null
docker volume rm test-rabbitmq-data 2>$null

Write-Host "🚀 Creando RabbitMQ de prueba..." -ForegroundColor Green
docker run -d `
  --name test-rabbitmq `
  -e RABBITMQ_DEFAULT_USER=admin `
  -e RABBITMQ_DEFAULT_PASS=admin123 `
  -e RABBITMQ_ERLANG_COOKIE=CHANGEME_SECURE_COOKIE `
  -p 15672:15672 `
  -p 5672:5672 `
  rabbitmq:4-management

Write-Host "⏳ Esperando a que RabbitMQ inicie..." -ForegroundColor Cyan
for ($i = 1; $i -le 60; $i++) {
    $result = docker exec test-rabbitmq rabbitmq-diagnostics -q ping 2>$null
    if ($LASTEXITCODE -eq 0) {
        Write-Host "✅ RabbitMQ está respondiendo en intento $i" -ForegroundColor Green
        break
    }
    Write-Host "   Intento $i/60: RabbitMQ aún no responde..."
    Start-Sleep -Seconds 2
}

Write-Host ""
Write-Host "📋 Estado del contenedor:" -ForegroundColor Cyan
docker ps --filter name=test-rabbitmq

Write-Host ""
Write-Host "📋 Últimas líneas de logs:" -ForegroundColor Cyan
docker logs test-rabbitmq --tail 30

Write-Host ""
Write-Host "🌐 Puedes acceder a RabbitMQ Management en:" -ForegroundColor Yellow
Write-Host "   http://localhost:15672" -ForegroundColor White
Write-Host "   Usuario: admin" -ForegroundColor White
Write-Host "   Password: admin123" -ForegroundColor White
Write-Host ""
Write-Host "✅ Prueba completada. Para limpiar ejecuta:" -ForegroundColor Green
Write-Host "   docker rm -f test-rabbitmq" -ForegroundColor White
