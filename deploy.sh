#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

echo "=== Zhiyun CS deploy ==="

if [[ ! -f .env ]]; then
  cp .env.example .env
  echo "Created .env from .env.example"
  echo "Edit .env: set MOONSHOT_API_KEY or LLM_PROVIDER=ollama, then re-run."
  exit 1
fi

command -v docker >/dev/null || { echo "Docker required"; exit 1; }
command -v mvn >/dev/null || { echo "Maven required"; exit 1; }
command -v java >/dev/null || { echo "JDK 17 required"; exit 1; }
command -v npm >/dev/null || { echo "Node/npm required"; exit 1; }

echo "[1/3] backend package"
( cd backend && mvn -q -DskipTests package )
ls backend/target/*.jar >/dev/null

echo "[2/3] frontend build"
( cd frontend && { [[ -d node_modules ]] || npm ci; } && npm run build )
test -f frontend/dist/index.html

echo "[3/3] docker compose"
docker compose up -d --build

echo
echo "Open   http://localhost"
echo "Health http://localhost/api/health"
echo "Admin  from .env ADMIN_EMAIL / ADMIN_PASSWORD"
echo "Then set ADMIN_RESET_PASSWORD=false"
