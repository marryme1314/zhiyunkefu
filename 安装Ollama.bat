@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"
call "%~dp0tools\env.bat"

set "ROOT=%~dp0tools\Ollama"
set "ZIP=%~dp0tools\downloads\ollama-windows-amd64.zip"
set "TMP=%~dp0tools\tmp-ollama-extract"
set "OLLAMA_MODELS=%~dp0tools\Ollama\models"

echo Install portable Ollama to %ROOT%
if not exist "%~dp0tools\downloads" mkdir "%~dp0tools\downloads"
if not exist "%ROOT%" mkdir "%ROOT%"
if not exist "%OLLAMA_MODELS%" mkdir "%OLLAMA_MODELS%"

if not exist "%ZIP%" (
  echo Package missing: %ZIP%
  echo Downloading via GitHub proxy (~1.4GB)...
  curl.exe -L --retry 8 --retry-delay 3 -o "%ZIP%" "https://gh.ddlc.top/https://github.com/ollama/ollama/releases/download/v0.33.1/ollama-windows-amd64.zip"
  if errorlevel 1 (
    echo Download failed. Place ollama-windows-amd64.zip under tools\downloads\ and retry.
    pause
    exit /b 1
  )
)

echo Extracting to %ROOT%
if exist "%TMP%" rmdir /s /q "%TMP%"
mkdir "%TMP%"
tar.exe -xf "%ZIP%" -C "%TMP%"
if errorlevel 1 (
  echo Extract failed
  pause
  exit /b 1
)

xcopy /e /y /q "%TMP%\*" "%ROOT%\" >nul
if exist "%TMP%\ollama.exe" copy /y "%TMP%\ollama.exe" "%ROOT%\ollama.exe" >nul
rmdir /s /q "%TMP%"

if not exist "%ROOT%\ollama.exe" (
  echo ollama.exe still missing after extract
  pause
  exit /b 1
)

echo.
echo Done: %ROOT%\ollama.exe
echo Models dir: %OLLAMA_MODELS%
echo Next: run 启动Ollama.bat then 拉取Ollama模型.bat
pause
