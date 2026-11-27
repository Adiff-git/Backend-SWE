#!/usr/bin/env bash
# Exactly two modes; operational helpers are private and never cloud targets.
set -Eeuo pipefail
umask 077

fail() { printf '%s\n' "$2" >&2; exit "$1"; }
mode=normal
if (( $# )); then
    [[ $# == 1 && $1 == --fresh ]] || fail 2 'Usage: bash scripts/start-dev.sh [--fresh]'
    mode=fresh
fi
[[ $(uname -s) == Linux ]] || fail 2 'Run this entry point in Linux or WSL2 Bash.'
command -v docker >/dev/null || fail 2 'Docker CLI is required.'
command -v git >/dev/null || fail 2 'Git is required for source revision verification.'
for override in AWS_PROFILE AWS_DEFAULT_PROFILE AWS_ENDPOINT_URL AWS_ENDPOINT_URL_S3 DB_URL DATABASE_URL; do
    [[ -z ${!override:-} ]] || fail 2 'Ambient cloud/database overrides are forbidden.'
done
while IFS= read -r override; do
    [[ $override != PG* || -z ${!override:-} ]] || fail 2 'Ambient PostgreSQL session/discovery overrides are forbidden.'
done < <(compgen -e)
repo=$(CDPATH='' cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)
[[ -r $repo/.env.local ]] || fail 2 'Configure the uncommitted .env.local before startup.'
image=backend-swe-demo-bootstrap:local
project=backend-swe-demo
lock_name=backend-swe-demo-bootstrap-lock
compose=(docker compose --project-name "$project" --env-file "$repo/.env.local" -f "$repo/compose.dev.yaml")
export BOOTSTRAP_HOST_GID
BOOTSTRAP_HOST_GID=$(id -g)
endpoint=$(docker context inspect --format '{{.Endpoints.docker.Host}}') || fail 2 'Cannot inspect local Docker context.'
if [[ -n ${DOCKER_HOST:-} ]]; then endpoint=$DOCKER_HOST; fi
case "$endpoint" in unix:///*|npipe:////./pipe/dockerDesktopLinuxEngine) ;; *) fail 2 'Only a local Docker daemon is permitted.' ;; esac
docker info >/dev/null 2>&1 || fail 5 'Local Docker daemon is unavailable.'
BOOTSTRAP_DAEMON_ID=$(docker info --format '{{.ID}}') || fail 5 'Cannot establish Docker daemon identity.'
[[ -n $BOOTSTRAP_DAEMON_ID ]] || fail 5 'Docker daemon identity is absent.'
export BOOTSTRAP_DAEMON_ID
docker image inspect "$image" >/dev/null 2>&1 || fail 2 'Build the approved tools/demo-bootstrap image first.'
[[ $(git -C "$repo/data/Catalog-Data" rev-parse HEAD) == c5dcfaabdf61b244783d46a1403107e19cd2b02c ]] || fail 4 'Source Git revision is incompatible.'

# Compose parses .env as data; helper consumes JSON over stdin, never shell-sources it.
timeout_values=$("${compose[@]}" config --format json |
    docker run --rm -i --network none --mount "type=bind,source=$repo/tools/demo-bootstrap,target=/app,readonly" \
        --entrypoint python "$image" /app/bootstrap.py validate-compose --format timeouts) || fail 2 'Effective Compose target/configuration is invalid.'
IFS=$'\t' read -r infrastructure_timeout backend_timeout storage_timeout <<< "$timeout_values"

if [[ $mode == fresh ]]; then
    printf 'Fresh startup resets only backend_demo and importer-owned assets. Type RESET backend_demo: ' >&2
    read -r confirmation || fail 2 'Fresh startup confirmation was not supplied.'
    [[ $confirmation == 'RESET backend_demo' ]] || fail 2 'Fresh startup confirmation did not match; nothing was reset.'
fi

token=$(cat /proc/sys/kernel/random/uuid)
lock_id=''
child_pid=''
migrator_id=''
worker_name=''
cleanup() {
    local status=$?
    trap - EXIT INT TERM
    if [[ -n $child_pid ]]; then
        kill -TERM "$child_pid" 2>/dev/null || true
        wait "$child_pid" 2>/dev/null || true
    fi
    local safe_to_release=true worker worker_owner state
    for worker in "$migrator_id" "$worker_name"; do
        [[ -n $worker ]] || continue
        # A lost client acknowledgement is not proof that its server-side worker stopped.
        if docker inspect "$worker" >/dev/null 2>&1; then
            worker_owner=$(docker inspect --format '{{index .Config.Labels "demo-bootstrap.invocation"}}' "$worker" 2>/dev/null || true)
            if [[ $worker_owner != "$token" ]]; then safe_to_release=false; continue; fi
            docker stop --time 30 "$worker" >/dev/null 2>&1 || safe_to_release=false
            state=$(docker inspect --format '{{.State.Running}}' "$worker" 2>/dev/null || true)
            if [[ $state == false ]]; then docker rm "$worker" >/dev/null 2>&1 || true
            else safe_to_release=false; fi
        elif ! docker info >/dev/null 2>&1; then
            safe_to_release=false
        fi
    done
    if [[ -n $lock_id && $safe_to_release == true ]]; then
        current=$(docker inspect --format '{{index .Config.Labels "demo-bootstrap.invocation"}}' "$lock_id" 2>/dev/null || true)
        if [[ $current == "$token" ]]; then docker rm "$lock_id" >/dev/null 2>&1 || true; fi
    fi
    exit "$status"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
lock_id=$(docker create --name "$lock_name" --network none \
    --label "demo-bootstrap.invocation=$token" --label "demo-bootstrap.project=$project" \
    --label demo-bootstrap.database=backend_demo --label demo-bootstrap.bucket=backend-demo-images \
    --label "demo-bootstrap.host=$(hostname)" --label "demo-bootstrap.pid=$$" \
    --label "demo-bootstrap.started=$(date -u +%FT%TZ)" \
    --entrypoint python "$image" -c 'pass' 2>/dev/null) || fail 3 'Bootstrap lock is held or could not be reserved; no competing mutation is allowed.'

inspect_owned_volume() {
    local volume=$1 names owner found=false name
    names=$(docker volume ls --format '{{.Name}}') || fail 5 'Cannot determine existing volumes safely.'
    while IFS= read -r name; do
        if [[ $name == "$volume" ]]; then found=true; break; fi
    done <<< "$names"
    if [[ $found == true ]]; then
        owner=$(docker volume inspect --format '{{index .Labels "com.docker.compose.project"}}' "$volume") || fail 5 'Cannot inspect stateful volume ownership.'
        [[ $owner == "$project" ]] || fail 4 'Stateful volume is not owned by the dedicated project.'
        return 0
    fi
    return 1  # Proven absent; do not create by mounting during inspection.
}

wait_service() {
    local service=$1 timeout=$2 deadline=$((SECONDS + $2)) id state health
    while (( SECONDS < deadline )); do
        id=$("${compose[@]}" ps --all --quiet "$service") || return 5
        if [[ -n $id ]]; then
            [[ $(docker inspect --format '{{index .Config.Labels "com.docker.compose.project"}}' "$id") == "$project" ]] || return 4
            state=$(docker inspect --format '{{.State.Status}}' "$id") || return 5
            [[ $state != exited && $state != dead ]] || return 5
            health=$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}absent{{end}}' "$id") || return 5
            [[ $health != healthy ]] || return 0
        fi
        sleep 1
    done
    printf 'Readiness deadline exceeded for %s (%s seconds).\n' "$service" "$timeout" >&2
    return 5
}

assert_no_stateful_recreation() {
    local service=$1 id hash_line expected_hash actual_hash owner parent namespace
    id=$("${compose[@]}" ps --all --quiet "$service") || fail 5 'Cannot inspect existing service state.'
    [[ -n $id ]] || return 0
    owner=$(docker inspect --format '{{index .Config.Labels "com.docker.compose.project"}}' "$id") || fail 5 'Cannot inspect existing service ownership.'
    [[ $owner == "$project" ]] || fail 4 'Existing service is not owned by the dedicated project.'
    if [[ $service == image-storage ]]; then
        parent=$("${compose[@]}" ps --all --quiet localstack) || fail 5 'Cannot inspect gateway namespace parent.'
        [[ -n $parent ]] || fail 4 'Storage gateway has no owned LocalStack namespace parent.'
        parent=$(docker inspect --format '{{.Id}}' "$parent") || fail 5 'Cannot resolve namespace identity.'
        namespace=$(docker inspect --format '{{.HostConfig.NetworkMode}}' "$id") || fail 5 'Cannot inspect gateway namespace.'
        [[ $namespace == "container:$parent" ]] || fail 4 'Storage gateway shares an unexpected namespace.'
        hash_line=$(printf '{"services":{"image-storage":{"network_mode":"container:%s"}}}' "$parent" |
            "${compose[@]}" -f - config --hash "$service") || fail 5 'Cannot determine resolved gateway configuration identity.'
    else
        hash_line=$("${compose[@]}" config --hash "$service") || fail 5 'Cannot determine intended service configuration identity.'
    fi
    read -r _ expected_hash <<< "$hash_line"
    [[ $expected_hash =~ ^[0-9a-f]{64}$ ]] || fail 5 'Invalid service configuration fingerprint.'
    actual_hash=$(docker inspect --format '{{index .Config.Labels "com.docker.compose.config-hash"}}' "$id") || fail 5 'Cannot inspect existing service configuration identity.'
    [[ $actual_hash == "$expected_hash" ]] || fail 4 'Stateful service configuration changed; automatic recreation is forbidden.'
}

run_supervised() {
    "$@" &
    child_pid=$!
    local result=0
    wait "$child_pid" || result=$?
    child_pid=''
    return "$result"
}

for required_volume in "${project}_postgres-data" "${project}_product-checkpoints"; do
    inspect_owned_volume "$required_volume" || {
        result=$?
        [[ $result == 1 ]] || fail "$result" 'Volume inspection failed.'
    }
done
for stateful_service in postgres localstack image-storage mailpit; do
    assert_no_stateful_recreation "$stateful_service"
done
network_names=$(docker network ls --format '{{.Name}}') || fail 5 'Cannot inspect project networks.'
for network in "${project}_default" "${project}_storage-private"; do
    while IFS= read -r existing_network; do
        [[ $existing_network == "$network" ]] || continue
        network_owner=$(docker network inspect --format '{{index .Labels "com.docker.compose.project"}}' "$network") || fail 5 'Cannot inspect network ownership.'
        network_driver=$(docker network inspect --format '{{.Driver}}' "$network") || fail 5 'Cannot inspect network driver.'
        [[ $network_owner == "$project" && $network_driver == bridge ]] || fail 4 'Existing network is not an owned local project bridge.'
    done <<< "$network_names"
done

# Resolve artifact as Compose data; never evaluate an .env value as shell code.
full_compose=("${compose[@]}" -f "$repo/compose.bootstrap.yaml")
artifact=$("${full_compose[@]}" config --format json |
    docker run --rm -i --network none --entrypoint python "$image" /app/bootstrap.py host-artifact) || fail 2 'Configure an existing reviewed private artifact.'
artifact=$(realpath -e -- "$artifact") || fail 2 'Reviewed artifact directory is missing.'
case "$artifact" in "$repo"/.private/demo-bootstrap/artifacts/*) ;; *) fail 2 'Artifact must be under the private artifacts directory.' ;; esac
[[ -d $artifact && ! -L $artifact ]] || fail 2 'Reviewed artifact must be a regular private directory.'
run_supervised docker run --rm --network none \
    --mount "type=bind,source=$artifact,target=/artifact,readonly" \
    --mount "type=bind,source=$repo/data/Catalog-Data,target=/source,readonly" \
    --mount "type=bind,source=$repo/demo/src/main/resources/db/migration,target=/migrations,readonly" \
    --mount "type=bind,source=$repo/scripts/start-dev.sh,target=/startup-script,readonly" \
    --entrypoint python "$image" /app/runtime.py artifact || fail 4 'Artifact/source/importer/migration verification failed; nothing was reset.'

# All initialization work starts with application traffic stopped. An absent
# service is valid; never start it merely to stop it.
backend_id=$("${compose[@]}" ps --all --quiet backend) || fail 5 'Cannot inspect backend traffic state.'
if [[ -n $backend_id ]]; then
    [[ $(docker inspect --format '{{index .Config.Labels "com.docker.compose.project"}}' "$backend_id") == "$project" ]] || fail 4 'Backend ownership mismatch.'
    "${compose[@]}" stop backend >/dev/null || fail 5 'Cannot stop backend before initialization.'
fi
reports="$repo/.private/demo-bootstrap/reports"
mkdir -p -- "$reports"
chmod 2770 "$reports"
helper() {
    local stage=$1
    worker_name="$project-bootstrap-$token"
    run_supervised "${full_compose[@]}" run --rm --no-deps --name "$worker_name" \
        --label "demo-bootstrap.invocation=$token" --entrypoint python bootstrap /app/runtime.py "$stage"
    local result=$?
    if ! docker inspect "$worker_name" >/dev/null 2>&1 && docker info >/dev/null 2>&1; then worker_name=''; fi
    return "$result"
}
# Initialize checkpoint volume using the non-root helper's owned directory copy,
# before any existing service can populate a new volume as root.
if ! inspect_owned_volume "${project}_product-checkpoints"; then
    docker volume create --label "com.docker.compose.project=$project" \
        --label com.docker.compose.volume=product-checkpoints "${project}_product-checkpoints" >/dev/null || fail 5 'Checkpoint volume creation failed.'
    docker run --rm --network none --mount "type=volume,source=${project}_product-checkpoints,target=/catalog-checkpoints" \
        --entrypoint python "$image" -c 'import os; assert os.access("/catalog-checkpoints",os.W_OK)' || fail 5 'Non-root checkpoint initialization failed.'
fi
# Inspect root checkpoints before LocalStack's existing restore can write objects.
docker run --rm --network none \
    --mount "type=volume,source=${project}_product-checkpoints,target=/catalog-checkpoints,readonly" \
    --entrypoint python "$image" -c 'from pathlib import Path; from storage import inspect_checkpoints,OwnershipLedger; p=Path("/catalog-checkpoints"); files=inspect_checkpoints(p); OwnershipLedger(p,allow_new_empty=not files)' || fail 4 'Existing checkpoints or ownership are invalid; no deletion is authorized.'
localstack_id=$("${compose[@]}" ps --all --quiet localstack) || fail 5 'Cannot inspect storage service.'
if [[ -n $localstack_id && $(docker inspect --format '{{.State.Running}}' "$localstack_id") == true ]]; then
    # The helper networks must already exist for a running owned service. No
    # infrastructure recreation is allowed before checking live conflicts.
    helper live-preflight || fail 4 'Live object/checkpoint conflicts block service startup.'
fi
for infrastructure_service in postgres localstack image-storage mailpit; do
    "${compose[@]}" up -d --no-deps --no-recreate "$infrastructure_service" >/dev/null || fail 5 'Infrastructure startup failed.'
    wait_service "$infrastructure_service" "$infrastructure_timeout" || fail 5 'Infrastructure did not become ready.'
    if [[ $infrastructure_service == postgres && $mode == normal ]]; then
        helper restore-plan || fail 4 'Database/restore ownership is incompatible; storage restoration blocked.'
    fi
done
if [[ $mode == fresh ]]; then
    worker_name="$project-bootstrap-$token"
    run_supervised "${full_compose[@]}" run --rm --no-deps --name "$worker_name" \
        --label "demo-bootstrap.invocation=$token" -e BOOTSTRAP_FRESH_CONFIRMED=1 \
        --entrypoint python bootstrap /app/runtime.py reset || fail 5 'Fresh reset did not complete; retain saved ownership evidence.'
    worker_name=''
fi
state=$(helper classify) || fail 4 'Database state is foreign or incompatible; backend remains stopped.'
if [[ $state == RESET_PENDING ]]; then
    helper reset || fail 5 'Saved confirmed reset could not resume; preserve ownership evidence.'
    state=UNSEEDED
fi
if [[ $state != COMPLETED ]]; then
    [[ $state == UNSEEDED || $state == INTERRUPTED ]] || fail 4 'Unexpected initialization state.'
    # Existing backend only, no service ports, loopback container binding. The
    # state classifier proved business tables empty before scheduled code can run.
    "${compose[@]}" build backend >/dev/null || fail 5 'Existing backend image build failed.'
    migrator_name="$project-migrator-$token"
    migrator_id=$migrator_name
    "${compose[@]}" run -d --no-deps --name "$migrator_name" \
        --label "demo-bootstrap.invocation=$token" -e SERVER_ADDRESS=127.0.0.1 \
        -e SECURITY_IDENTITY_ENABLED=false -e SECURITY_VERIFICATION_ENABLED=false \
        -e SECURITY_VERIFICATION_MAIL_WORKER_ENABLED=false -e SECURITY_AUTH_CLEANUP_ENABLED=false \
        -e BEHAVIOR_RETENTION_ENABLED=false backend >/dev/null || fail 5 'Temporary migration instance failed to start.'
    deadline=$((SECONDS + backend_timeout))
    migrated=false
    while (( SECONDS < deadline )); do
        running=$(docker inspect --format '{{.State.Running}}' "$migrator_id") || fail 5 'Lost migration instance.'
        [[ $running == true ]] || fail 5 'Temporary backend exited before readiness.'
        if docker exec "$migrator_id" wget -q -O /dev/null 'http://127.0.0.1:8080/api/products?q=catalog-readiness-probe&page=0&size=1'; then migrated=true; break; fi
        sleep 1
    done
    [[ $migrated == true ]] || fail 5 'Temporary migration readiness deadline exceeded.'
    docker stop --time 30 "$migrator_id" >/dev/null || fail 5 'Cannot stop migrator before business import.'
    docker rm "$migrator_id" >/dev/null || fail 5 'Cannot remove owned stopped migrator.'
    migrator_id=''
    helper migrated || fail 4 'Actual Flyway/extension history is incompatible.'
fi
helper import || { result=$?; fail "$result" 'Initialization or asset repair failed; backend remains stopped. Read the reported commit state before retry.'; }
"${compose[@]}" up -d --no-deps backend >/dev/null || fail 6 'Dataset committed; backend failed to start. Do not reset.'
wait_service backend "$backend_timeout" || fail 6 'Dataset committed; backend readiness failed. Do not reset.'
helper report-ready || fail 6 'Dataset committed; backend is ready but final evidence could not be written.'
