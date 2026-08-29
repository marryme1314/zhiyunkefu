@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"
call "%~dp0tools\env.bat"
set "OLLAMA_MODELS=%~dp0tools\Ollama\models"
set "OLLAMA_HOST=127.0.0.1:11434"
if not exist "%OLLAMA_MODELS%" mkdir "%OLLAMA_MODELS%"
echo Pulling chat + embedding models into %OLLAMA_MODELS%
echo Keep 启动Ollama.bat running in another window.
echo.
"%~dp0tools\Ollama\ollama.exe" pull qwen2
"%~dp0tools\Ollama\ollama.exe" pull nomic-embed-text
echo.
echo Done. List models with: "%~dp0tools\Ollama\ollama.exe" list
pause
