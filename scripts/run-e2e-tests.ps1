# Script para ejecutar SOLO los tests E2E (asume que los servicios ya están corriendo)
$ErrorActionPreference = "Continue"

Write-Host "📋 Verificando que los servicios estén corriendo..." -ForegroundColor Cyan
$runningContainers = docker ps --filter "name=rabbitmq" --format "{{.Names}}"
if (-not $runningContainers) {
    Write-Host "❌ ERROR: Los servicios no están corriendo." -ForegroundColor Red
    Write-Host "   Ejecuta primero: .\scripts\ci-e2e-up.ps1" -ForegroundColor Yellow
    exit 1
}

Write-Host "✅ Servicios corriendo" -ForegroundColor Green

Write-Host ""
Write-Host "🧪 Ejecutando tests E2E..." -ForegroundColor Cyan
Set-Location e2e-tests

# Instalar dependencias si no existen
if (-not (Test-Path "node_modules")) {
    Write-Host "📦 Instalando dependencias de npm..." -ForegroundColor Yellow
    npm ci --no-audit --no-fund
}

# Ejecutar tests
Write-Host ""
Write-Host "🚀 Ejecutando Cucumber tests..." -ForegroundColor Green
$env:BASE_URL = "http://localhost:8080"
npx cucumber-js

$exitCode = $LASTEXITCODE

Set-Location ..

if ($exitCode -eq 0) {
    Write-Host ""
    Write-Host "✅ Tests E2E pasaron exitosamente!" -ForegroundColor Green
} else {
    Write-Host ""
    Write-Host "❌ Tests E2E fallaron con código: $exitCode" -ForegroundColor Red
    Write-Host ""
    Write-Host "💡 Tips de diagnóstico:" -ForegroundColor Yellow
    Write-Host "   1. Ver logs del API Gateway: docker logs api-gateway --tail 50" -ForegroundColor White
    Write-Host "   2. Ver logs de RabbitMQ: docker logs rabbitmq --tail 50" -ForegroundColor White
    Write-Host "   3. Ver estado de servicios: docker ps" -ForegroundColor White
}

exit $exitCode
