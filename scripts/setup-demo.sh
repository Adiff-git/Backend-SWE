#!/usr/bin/env bash
set -euo pipefail

backend_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
catalog_root="${CATALOG_DATA_DIR:-$(cd "$backend_root/.." && pwd)/Catalog-Data}"
env_file="${DEMO_ENV_FILE:-$backend_root/.env.demo.local}"

mode=()
if [[ ${1:-} == --data-only ]]; then
  mode=(--data-only)
  shift
fi
python3 "$backend_root/scripts/demo_preflight.py" --catalog "$catalog_root" --env-file "$env_file" "${mode[@]}"
python3 "$backend_root/scripts/run_demo.py" "$catalog_root" "$env_file" "${mode[@]}" "$@"
