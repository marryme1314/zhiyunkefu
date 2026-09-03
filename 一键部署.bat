@echo off
chcp 65001 >nul
setlocal EnableExtensions
cd /d "%~dp0"
call "%~dp0tools\env.bat" 2>nul
set "PATH=C:\Program Files\Docker\Docker\resources\bin;%PATH%"

echo === Zhiyun CS one-click deploy ===
echo.

if not exist ".env" (
  if exist ".env.example" (
    copy /Y ".env.example" ".env" >nul
    echo [OK] Created .env from .env.example
    echo [!!] Edit .env: set MOONSHOT_API_KEY  OR  set LLM_PROVIDER=ollama
    echo     Then run this script again.
    notepad ".env"
    pause
    exit /b 1
  ) else (
    echo Missing .env.example
    pause
    exit /b 1
  )
)

where docker >nul 2>&1
if errorlevel 1 (
  echo [X] Docker not found. Install Docker Desktop and retry.
  pause
  exit /b 1
)
where mvn >nul 2>&1
if errorlevel 1 (
  echo [X] Maven not found. Install JDK 17 + Maven, or put mvn on PATH.
  pause
  exit /b 1
)
where java >nul 2>&1
if errorlevel 1 (
  echo [X] Java not found. Install JDK 17.
  pause
  exit /b 1
)
where npm >nul 2>&1
if errorlevel 1 (
  echo [X] npm not found. Install Node.js 18+.
  pause
  exit /b 1
)

findstr /B /C:"MOONSHOT_API_KEY=" ".env" | findstr /V /C:"MOONSHOT_API_KEY=$" | findstr /V /C:"MOONSHOT_API_KEY= " >nul
set "HAS_KEY=0"
for /f "usebackq tokens=1,* delims==" %%A in (".env") do (
  if /I "%%A"=="MOONSHOT_API_KEY" if not "%%B"=="" set "HAS_KEY=1"
)
set "USE_OLLAMA=0"
for /f "usebackq tokens=1,* delims==" %%A in (".env") do (
  if /I "%%A"=="LLM_PROVIDER" if /I "%%B"=="ollama" set "USE_OLLAMA=1"
)
if "%HAS_KEY%"=="0" if "%USE_OLLAMA%"=="0" (
  echo [!!] MOONSHOT_API_KEY is empty and LLM_PROVIDER is not ollama.
  echo      Chat will fail in Docker prod. Fill the key or set LLM_PROVIDER=ollama.
  echo.
)

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
dir /b "%~dp0backend\target\*.jar" >nul 2>&1
if errorlevel 1 (
  echo backend jar missing under backend\target
  pause
  exit /b 1
)

echo [2/3] Build frontend dist...
pushd "%~dp0frontend"
if not exist node_modules (
  if exist package-lock.json (call npm ci) else (call npm install)
)
call npm run build
if errorlevel 1 (
  echo frontend build failed
  popd
  pause
  exit /b 1
)
popd
if not exist "%~dp0frontend\dist\index.html" (
  echo frontend dist\index.html missing
  pause
  exit /b 1
)

echo [3/3] Docker compose up --build ...
docker compose -f "%~dp0docker-compose.yml" up -d --build
if errorlevel 1 (
  echo docker compose failed
  pause
  exit /b 1
)

echo.
echo Done.
echo Open   http://localhost
echo Health http://localhost/api/health
echo Admin  = ADMIN_EMAIL / ADMIN_PASSWORD in .env
echo After first login, set ADMIN_RESET_PASSWORD=false in .env
echo.
pause
