@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"
call "%~dp0tools\env.bat"

if exist "%~dp0.env" (
  for /f "usebackq eol=# tokens=1,* delims==" %%A in ("%~dp0.env") do (
    if not "%%A"=="" set "%%A=%%B"
  )
)

if not defined JWT_SECRET set "JWT_SECRET=please-change-this-to-a-long-random-secret"
if not defined MYSQL_HOST set "MYSQL_HOST=localhost"
if not defined MYSQL_PORT set "MYSQL_PORT=3307"
if not defined MYSQL_DATABASE set "MYSQL_DATABASE=ai_cs"
if not defined MYSQL_USER set "MYSQL_USER=root"
if not defined MYSQL_PASSWORD set "MYSQL_PASSWORD=ai_cs_dev"
if not defined OLLAMA_BASE_URL set "OLLAMA_BASE_URL=http://localhost:11434"
if not defined OLLAMA_CHAT_MODEL set "OLLAMA_CHAT_MODEL=qwen2"
if not defined OLLAMA_EMBED_MODEL set "OLLAMA_EMBED_MODEL=nomic-embed-text"

echo Starting Spring Boot (JDK + Maven, IDEA not required).
echo MySQL default: localhost:3307 / ai_cs
echo Chat: Moonshot if MOONSHOT_API_KEY set; else Ollama.
echo Embed: Ollama nomic-embed-text if available; else local lexical vectors.
echo.
cd /d "%~dp0backend"
call mvn spring-boot:run
pause
