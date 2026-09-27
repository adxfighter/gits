#!/usr/bin/env bash
# Stops the GITS local stack. Pass --purge to also delete the database volume.
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ "${1:-}" == "--purge" ]]; then docker compose down -v; else docker compose down; fi
