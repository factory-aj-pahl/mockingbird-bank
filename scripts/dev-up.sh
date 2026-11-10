#!/usr/bin/env bash
# Single command from a fresh clone to a running app: starts (or reuses) the
# local Postgres from docker-compose.yml, waits for it to be healthy, then
# runs the app against it in the foreground.
#
# Unlike scripts/run-interactive-qa.sh, this Postgres is *not* torn down on
# exit - it's the same named volume across runs (see docker-compose.yml), so
# your data survives between `dev-up.sh` sessions. Stop it explicitly with
# `docker compose down` (add `-v` to also wipe the data) when you're done for
# good.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

echo "== Mockingbird Bank: dev-up =="

echo "Starting local Postgres (docker compose up -d)..."
docker compose up -d --wait

echo "Postgres is healthy. Starting the app (./gradlew bootRun)..."
echo "Sign in at http://localhost:8080 with jordan.ellis or riley.chen / mockingbird (Ctrl+C to stop)."
exec ./gradlew bootRun
