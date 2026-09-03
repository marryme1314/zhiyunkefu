@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"
call "%~dp0tools\env.bat"

set "OLLAMA_ROOT=%~dp0tools\Ollama"
set "OLLAMA_MODELS=%~dp0tools\Ollama\models"
rem 0.0.0.0：本机与 Docker 容器（host.docker.internal）都能访问
set "OLLAMA_HOST=0.0.0.0:11434"
if exist "%OLLAMA_ROOT%\ollama.exe" set "PATH=%OLLAMA_ROOT%;%PATH%"

if not exist "%OLLAMA_ROOT%\ollama.exe" (
  echo Missing %OLLAMA_ROOT%\ollama.exe
  echo Run 安装Ollama.bat first, or install Ollama system-wide.
  pause
  exit /b 1
)

if not exist "%OLLAMA_MODELS%" mkdir "%OLLAMA_MODELS%"

set "CUDA_VISIBLE_DEVICES="
set "OLLAMA_NUM_GPU=0"

echo Starting Ollama (models: %OLLAMA_MODELS%, CPU mode)
"%OLLAMA_ROOT%\ollama.exe" serve
pause
