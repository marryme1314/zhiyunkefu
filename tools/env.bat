@echo off
REM Add optional local Git / Docker / Ollama to PATH for this console.
set "ROOT=%~dp0.."
if exist "%ROOT%\tools\Git\cmd\git.exe" set "PATH=%ROOT%\tools\Git\cmd;%PATH%"
if exist "C:\Program Files\Git\cmd\git.exe" set "PATH=C:\Program Files\Git\cmd;%PATH%"
if exist "C:\Program Files\Docker\Docker\resources\bin\docker.exe" set "PATH=C:\Program Files\Docker\Docker\resources\bin;%PATH%"
if exist "%ROOT%\tools\Ollama\ollama.exe" set "PATH=%ROOT%\tools\Ollama;%PATH%"
if exist "%LOCALAPPDATA%\Programs\Ollama\ollama.exe" set "PATH=%LOCALAPPDATA%\Programs\Ollama;%PATH%"
set "OLLAMA_MODELS=%ROOT%\tools\Ollama\models"
set "OLLAMA_HOST=127.0.0.1:11434"
if not exist "%ROOT%\tools\tmp" mkdir "%ROOT%\tools\tmp"
set "TEMP=%ROOT%\tools\tmp"
set "TMP=%ROOT%\tools\tmp"
