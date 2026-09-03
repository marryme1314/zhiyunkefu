@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"
if not exist ".env" (
  echo 请先复制 .env.example 为 .env，并填写 JWT_SECRET、ADMIN_PASSWORD、MOONSHOT_API_KEY
  pause
  exit /b 1
)
set "PATH=C:\Program Files\Docker\Docker\resources\bin;%PATH%"
call "%~dp0tools\env.bat" 2>nul

echo [1/3] Build backend jar...
pushd "%~dp0backend"
call mvn -q -DskipTests package
if errorlevel 1 (
  echo backend package failed
  popd
  pause
  exit /b 1
)
popd

echo [2/3] Build frontend dist...
pushd "%~dp0frontend"
if not exist node_modules call npm ci
call npm run build
if errorlevel 1 (
  echo frontend build failed
  popd
  pause
  exit /b 1
)
popd

echo [3/3] Docker compose up...
docker compose -f "%~dp0docker-compose.yml" up -d --build
if errorlevel 1 (
  echo docker compose failed
  pause
  exit /b 1
)
echo.
echo Open http://localhost
echo Health http://localhost/api/health
echo Admin = ADMIN_EMAIL / ADMIN_PASSWORD in .env
echo After first reset, set ADMIN_RESET_PASSWORD=false
pause
