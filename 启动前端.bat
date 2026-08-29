@echo off
cd /d "%~dp0frontend"
if not exist node_modules (
  echo First run: npm install ...
  call npm install
)
echo Frontend: http://localhost:5173
call npm run dev
pause
