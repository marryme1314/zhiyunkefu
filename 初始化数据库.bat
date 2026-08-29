@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"
set "PATH=C:\Program Files\Docker\Docker\resources\bin;%PATH%"

echo Starting Docker Desktop if needed...
start "" "C:\Program Files\Docker\Docker\Docker Desktop.exe"

echo Waiting for Docker engine...
set /a n=0
:waitdocker
"C:\Program Files\Docker\Docker\resources\bin\docker.exe" info >nul 2>&1
if %errorlevel%==0 goto up
set /a n+=1
if %n% geq 60 (
  echo Docker is not ready. Open Docker Desktop and retry.
  pause
  exit /b 1
)
timeout /t 2 /nobreak >nul
goto waitdocker

:up
echo Starting MySQL container (port 3307, database ai_cs)...
"C:\Program Files\Docker\Docker\resources\bin\docker.exe" compose -f "%~dp0docker-compose.yml" up -d
if errorlevel 1 (
  echo docker compose failed
  pause
  exit /b 1
)
echo MySQL ready: root / ai_cs_dev @ localhost:3307
pause
