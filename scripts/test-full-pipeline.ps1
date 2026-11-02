# Script para probar el pipeline completo localmente (simula lo que hace Jenkins)
$ErrorActionPreference = "Stop"

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  TEST PIPELINE COMPLETO (Simulación)" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

$startTime = Get-Date

# Stage 1: Build (Maven)
Write-Host "📦 Stage 1: Build (gestion-empleados)" -ForegroundColor Yellow
Set-Location microservicios/gestion-empleados
mvn clean compile -DskipTests
if ($LASTEXITCODE -ne 0) { 
    Write-Host "❌ Build failed" -ForegroundColor Red
    exit 1 
}
Set-Location ../..
Write-Host "✅ Build completado`n" -ForegroundColor Green

# Stage 2: Test (Maven)
Write-Host "🧪 Stage 2: Test (gestion-empleados)" -ForegroundColor Yellow
Set-Location microservicios/gestion-empleados
mvn test
if ($LASTEXITCODE -ne 0) { 
    Write-Host "❌ Tests failed" -ForegroundColor Red
    exit 1 
}
Set-Location ../..
Write-Host "✅ Tests completados`n" -ForegroundColor Green

# Stage 3: Package (Docker build)
Write-Host "📦 Stage 3: Package (Docker)" -ForegroundColor Yellow
Set-Location microservicios/gestion-empleados
docker build -t test-gestion-empleados:latest .
if ($LASTEXITCODE -ne 0) { 
    Write-Host "❌ Docker build failed" -ForegroundColor Red
    exit 1 
}
Set-Location ../..
Write-Host "✅ Package completado`n" -ForegroundColor Green

# Stage 4: E2E Tests
Write-Host "🔧 Stage 4: Levantar entorno E2E" -ForegroundColor Yellow
.\scripts\ci-e2e-up.ps1
if ($LASTEXITCODE -ne 0) { 
    Write-Host "❌ No se pudo levantar entorno E2E" -ForegroundColor Red
    exit 1 
}
Write-Host ""

Write-Host "🧪 Stage 5: Ejecutar tests E2E" -ForegroundColor Yellow
.\scripts\run-e2e-tests.ps1
$e2eResult = $LASTEXITCODE

# Cleanup
Write-Host ""
Write-Host "🧹 Limpiando entorno E2E..." -ForegroundColor Yellow
docker compose -p reto7-e2e -f docker-compose.yml -f docker-compose.e2e.yml down -v 2>$null

$endTime = Get-Date
$duration = $endTime - $startTime

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  RESULTADO DEL PIPELINE" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

if ($e2eResult -eq 0) {
    Write-Host "✅ PIPELINE COMPLETO EXITOSO" -ForegroundColor Green
    Write-Host "   Duración total: $($duration.Minutes)m $($duration.Seconds)s" -ForegroundColor White
    exit 0
} else {
    Write-Host "❌ PIPELINE FALLÓ EN E2E TESTS" -ForegroundColor Red
    Write-Host "   Duración total: $($duration.Minutes)m $($duration.Seconds)s" -ForegroundColor White
    exit 1
}
