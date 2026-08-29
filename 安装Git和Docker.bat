@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"
echo ===== Install Git into %~dp0tools\Git =====
winget uninstall --id Git.Git -e --disable-interactivity --accept-source-agreements
winget install --id Git.Git -e --source winget --accept-package-agreements --accept-source-agreements --disable-interactivity --location "%~dp0tools\Git"

echo.
echo ===== Install Docker Desktop (may prompt for admin) =====
winget install --id Docker.DockerDesktop -e --source winget --accept-package-agreements --accept-source-agreements --disable-interactivity

echo.
echo Reopen the terminal, then check:
echo   git --version
echo   docker --version
pause
