#!/usr/bin/env bash
# One-command local startup after an approved private seed image is available.
set -euo pipefail
cd "$(dirname "$0")/../.."

seed_image="${WISHLIST_SEED_IMAGE:-hza206/catalog:wishlist-v1}"
env_file=.env.wishlist.local
seed_dir=tools/catalog-import/output/catalog-wishlist-v1
venv_dir=.wishlist-venv

if [[ ! -f "$env_file" ]]; then
  python3 - <<'PY'
import os,secrets
values={
 'COMPOSE_PROJECT_NAME':'backend-wishlist-local',
 'LOCAL_DB_NAME':'backend','LOCAL_DB_USER':'backend',
 'LOCAL_DB_HOST':'127.0.0.1','LOCAL_DB_PORT':'55434',
 'LOCAL_DB_PASSWORD':secrets.token_urlsafe(36),
 'JWT_SECRET':secrets.token_urlsafe(48),
 'JWT_ISSUER':'urn:backend-swe:wishlist-local',
 'SERVER_ADDRESS':'127.0.0.1','SERVER_PORT':'18082',
}
values['CATALOG_IMPORT_DSN']='postgresql://backend:'+values['LOCAL_DB_PASSWORD']+'@127.0.0.1:55434/backend'
fd=os.open('.env.wishlist.local',os.O_CREAT|os.O_EXCL|os.O_WRONLY,0o600)
with os.fdopen(fd,'w') as file:
 for key,value in values.items(): file.write(f'{key}={value}\n')
PY
fi
set -a
source "$env_file"
set +a

docker image inspect "$seed_image" >/dev/null 2>&1 || docker pull "$seed_image"
mkdir -p "$seed_dir"
if [[ ! -f "$seed_dir/manifest.json" ]]; then
  docker run --rm -v "$PWD/$seed_dir:/seed-out" "$seed_image"
fi
(cd "$seed_dir" && sha256sum -c SHA256SUMS)
docker compose --env-file "$env_file" -f compose.yaml up -d --wait postgres

if ! curl -fsS "http://127.0.0.1:${SERVER_PORT}/api/products?page=0&size=1" >/dev/null 2>&1; then
  mvn -B -ntp -f demo/pom.xml spring-boot:run -Dspring-boot.run.profiles=local \
    > /tmp/backend-wishlist-local.log 2>&1 &
  for attempt in {1..120}; do
    if curl -fsS "http://127.0.0.1:${SERVER_PORT}/api/products?page=0&size=1" >/dev/null 2>&1; then break; fi
    if ! kill -0 "$!" 2>/dev/null; then
      echo 'Backend stopped; inspect /tmp/backend-wishlist-local.log' >&2; exit 1
    fi
    sleep 2
  done
fi
python3 -m venv "$venv_dir"
"$venv_dir/bin/pip" -q install -r tools/catalog-import/requirements-import.txt
seed_args=(--seed "$seed_dir" --target-kind local-development
  --expect-database backend --expect-host 127.0.0.1 --expect-port "$LOCAL_DB_PORT"
  --confirm-target "backend@127.0.0.1:$LOCAL_DB_PORT")
"$venv_dir/bin/python" tools/catalog-import/portable_seed.py "${seed_args[@]}"
"$venv_dir/bin/python" tools/catalog-import/portable_seed.py "${seed_args[@]}" --execute
python3 - <<'PY'
import json,os,urllib.request
url=f"http://127.0.0.1:{os.environ['SERVER_PORT']}/api/products?page=0&size=20"
page=json.load(urllib.request.urlopen(url,timeout=10))
if page['totalItems']!=1000: raise SystemExit('Expected exactly 1,000 visible products')
print(f"Ready: {page['totalItems']} products at {url}")
PY
