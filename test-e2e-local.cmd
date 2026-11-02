@echo off
REM Script para probar E2E localmente antes de subir a Git
echo ========================================
echo PRUEBA LOCAL DE E2E
echo ========================================
echo.

echo [1/5] Limpiando contenedores anteriores...
docker compose -f docker-compose.yml -f docker-compose.e2e.yml down -v
if errorlevel 1 (
    echo ERROR: No se pudo limpiar los contenedores
    exit /b 1
)
echo.

echo [2/5] Iniciando servicios en modo E2E...
docker compose -f docker-compose.yml -f docker-compose.e2e.yml up -d
if errorlevel 1 (
    echo ERROR: No se pudieron iniciar los servicios
    exit /b 1
)
echo.

echo [3/5] Esperando 60 segundos para que los servicios inicien...
timeout /t 60 /nobreak
echo.

echo [4/5] Verificando logs de RabbitMQ...
docker logs rabbitmq 2>&1 | findstr /i "error eacces cookie"
if errorlevel 1 (
    echo ✓ No se encontraron errores de permisos en RabbitMQ
) else (
    echo ✗ ALERTA: Se encontraron errores en RabbitMQ
    echo Mostrando ultimas lineas del log:
    docker logs rabbitmq --tail 20
)
echo.

echo [5/5] Ejecutando tests E2E...
cd e2e-tests
call npm test
set TEST_RESULT=%errorlevel%
cd ..

echo.
echo ========================================
if %TEST_RESULT%==0 (
    echo ✓ TESTS PASARON - Puedes hacer commit con confianza
) else (
    echo ✗ TESTS FALLARON - NO subas estos cambios
)
echo ========================================
echo.

echo Limpiando servicios...
docker compose -f docker-compose.yml -f docker-compose.e2e.yml down

exit /b %TEST_RESULT%
