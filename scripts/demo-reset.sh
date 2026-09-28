#!/usr/bin/env bash
# Resets the local demo (Linux/macOS): deletes the database, starts the stack (the task bank and the demo accounts
# are loaded by api at start) and seeds the recorded demo sessions from seed/demo-sessions.
# Usage: scripts/demo-reset.sh [--yes]
set -euo pipefail
cd "$(dirname "$0")/.."

if [[ "${1:-}" != "--yes" ]]; then
  read -r -p "Все данные локальной базы GITS будут удалены. Продолжить? (y/N) " answer
  [[ "$answer" == "y" || "$answer" == "Y" ]] || { echo "Отменено."; exit 1; }
fi

docker compose --profile tools down -v --remove-orphans
scripts/up.sh
# up returns once the containers start; the seed needs the schema, the bank and the demo employer made by api
docker compose up -d --wait
docker compose --profile tools run --rm --build taskbank demo-seed /seed/demo-sessions

port=$(grep -E '^WEB_PORT=' .env | cut -d= -f2)
echo "Готово. Кабинет работодателя: http://localhost:${port}/employer — балл и индикаторы демо-сессий появятся в течение минуты."
