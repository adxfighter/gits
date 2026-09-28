#!/usr/bin/env bash
# Starts the GITS local stack on Linux/macOS.
set -euo pipefail
cd "$(dirname "$0")/.."

command -v docker >/dev/null || { echo "Docker is not installed or not in PATH" >&2; exit 1; }

if [[ ! -f .env ]]; then
  cp .env.example .env
  echo "WARNING: .env created from .env.example - review the demo credentials before showing the demo." >&2
fi

env_value() { grep -E "^$1=" .env | head -1 | cut -d= -f2-; }

if [[ -f sandbox/java/Dockerfile ]]; then
  image="$(env_value SANDBOX_IMAGE)"
  image="${image:-gits-sandbox-java:local}"
  echo "Building sandbox image $image"
  docker build -t "$image" sandbox/java
fi

docker compose up --build -d --wait
docker compose ps
echo "GITS UI: http://localhost:$(env_value WEB_PORT)"
