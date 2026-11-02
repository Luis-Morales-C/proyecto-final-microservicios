@echo off
REM Script para verificar que toda la infraestructura CI este funcionando correctamente

echo ====================================
echo VERIFICACION DE INFRAESTRUCTURA CI
echo ====================================
echo.

echo [1/6] Verificando contenedores en ejecucion...
docker ps --filter "name=jenkins" --filter "name=sonarqube" --filter "name=registry" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"

echo.
echo [2/6] Verificando herramientas en Jenkins...
echo --- Maven ---
docker exec jenkins mvn --version 2>nul || echo ERROR: Maven no instalado
echo.
echo --- Node.js ---
docker exec jenkins node --version 2>nul || echo ERROR: Node.js no instalado
echo.
echo --- Python ---
docker exec jenkins python3 --version 2>nul || echo ERROR: Python no instalado
echo.
echo --- Docker CLI ---
docker exec jenkins docker --version 2>nul || echo ERROR: Docker CLI no instalado
echo.
echo --- SonarScanner CLI ---
docker exec jenkins sonar-scanner --version 2>nul || echo ERROR: SonarScanner no instalado

echo.
echo [3/6] Verificando conectividad Jenkins -> SonarQube...
docker exec jenkins curl -f -s http://sonarqube:9000/api/system/health 2>nul && echo OK: SonarQube accesible || echo ERROR: SonarQube no accesible

echo.
echo [4/6] Verificando conectividad Jenkins -> Registry...
docker exec jenkins curl -f -s http://registry:5000/v2/_catalog 2>nul && echo OK: Registry accesible || echo ERROR: Registry no accesible

echo.
echo [5/6] Verificando acceso externo...
curl -f -s http://localhost:9090 >nul 2>&1 && echo OK: Jenkins accesible en http://localhost:9090 || echo ERROR: Jenkins no accesible
curl -f -s http://localhost:9000 >nul 2>&1 && echo OK: SonarQube accesible en http://localhost:9000 || echo ERROR: SonarQube no accesible
curl -f -s http://localhost:5000/v2/_catalog >nul 2>&1 && echo OK: Registry accesible en http://localhost:5000 || echo ERROR: Registry no accesible

echo.
echo [6/6] Verificando volúmenes...
docker volume ls --filter "name=reto7-ci" --format "table {{.Name}}\t{{.Driver}}"

echo.
echo ====================================
echo VERIFICACION COMPLETA
echo ====================================
echo.
echo Siguiente paso: Ejecutar pipelines en Jenkins
echo 1. Accede a http://localhost:9090
echo 2. Ejecuta el pipeline "gestion-empleados"
echo 3. Ejecuta el pipeline "gestion-departamentos"
echo 4. Verifica en http://localhost:9000 que ambos proyectos aparezcan
echo.
