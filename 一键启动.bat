@echo off
cd /d "%~dp0"
echo Opening backend :8080 and frontend :5173
start "ai-cs-backend" cmd /k "%~dp0启动后端.bat"
timeout /t 3 /nobreak >nul
start "ai-cs-frontend" cmd /k "%~dp0启动前端.bat"
