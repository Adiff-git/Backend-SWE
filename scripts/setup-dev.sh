#!/usr/bin/env bash
# Linux/WSL Bash setup entry point. Windows uses setup-dev.ps1 directly.
set -Eeuo pipefail
fail() { printf 'Setup: %s\n' "$*" >&2; exit 2; }
if (( $# > 1 )) || { (( $# == 1 )) && [[ $1 != --fresh ]]; }; then fail 'Usage: bash scripts/setup-dev.sh [--fresh]'; fi
repo=$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)
case "$(uname -s)" in
  Linux) ;;
  *) fail 'Use Linux Bash, or run .\scripts\setup-dev.ps1 in Windows PowerShell.' ;;
esac
cd "$repo"
[[ -r .env.local ]] || fail 'Create and configure .env.local first.'
for cmd in git docker mktemp tar cp; do command -v "$cmd" >/dev/null || fail "Missing $cmd"; done
for key in AWS_PROFILE AWS_DEFAULT_PROFILE AWS_ENDPOINT_URL AWS_ENDPOINT_URL_S3 DB_URL DATABASE_URL; do [[ -z ${!key:-} ]] || fail 'Ambient cloud/database overrides forbidden.'; done
while IFS= read -r key; do [[ $key != PG* || -z ${!key:-} ]] || fail 'Ambient PostgreSQL overrides forbidden.'; done < <(compgen -e)
endpoint=$(docker context inspect --format '{{.Endpoints.docker.Host}}')
case "$endpoint" in unix:///*) ;; *) fail 'Only a local Docker daemon is permitted.' ;; esac
case "${DOCKER_HOST:-$endpoint}" in unix:///*) ;; *) fail 'Remote Docker daemon forbidden.' ;; esac
[[ $(docker info --format '{{.OSType}}') == linux ]] || fail 'Docker must run Linux containers.'
docker compose version >/dev/null
unset BOOTSTRAP_FRESH_CONFIRMED BOOTSTRAP_FAULT
export BOOTSTRAP_STARTUP_SCRIPT=./scripts/start-dev.sh
BOOTSTRAP_HOST_GID=$(id -g)
BOOTSTRAP_DAEMON_ID=$(docker info --format '{{.ID}}')
export BOOTSTRAP_HOST_GID BOOTSTRAP_DAEMON_ID
remote=https://github.com/Adiff-git/Catalog-Data.git
revision=89e3b6c5bc95b533386a349c65305613617d8709
source="$repo/data/Catalog-Data"
base="$repo/.private/demo-bootstrap/artifacts"
image=backend-swe-demo-bootstrap:local
for dir in "$repo/data" "$repo/.private" "$repo/.private/demo-bootstrap" "$base" "$source"; do [[ ! -L $dir ]] || fail "Symlink refused: $dir"; done
umask 027
mkdir -p "$base" "$repo/data"
# Both launchers use an atomic directory reservation. Never reclaim by age.
lock="$repo/.private/demo-bootstrap/setup.lock"
mkdir "$lock" 2>/dev/null || fail 'Setup lock exists; establish that no launcher is active before removing an abandoned setup.lock.'
src_tmp='' art_tmp=''
cleanup() { [[ -z $src_tmp ]] || rm -rf -- "$src_tmp"; [[ -z $art_tmp ]] || rm -rf -- "$art_tmp"; rmdir "$lock"; }
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
printf '[setup] Build helper (Docker cache enabled)\n'
docker build -t "$image" tools/demo-bootstrap
compose=(docker compose --project-name backend-swe-demo --env-file "$repo/.env.local" -f "$repo/compose.dev.yaml" -f "$repo/compose.bootstrap.yaml")
artifact=$("${compose[@]}" config --format json | docker run --rm -i --network none --entrypoint python "$image" /app/bootstrap.py host-artifact)
version=${artifact##*/}
[[ $version =~ ^demo-v[1-9][0-9]*$ && $artifact == "$base/$version" && ! -L $artifact ]] || fail 'Configure a versioned direct child of .private/demo-bootstrap/artifacts.'
printf '[setup] Prepare pinned source\n'
if [[ ! -e $source ]]; then
    src_tmp=$(mktemp -d "$repo/data/.catalog-setup.XXXXXXXX")
    git -c core.autocrlf=false clone --no-checkout --no-tags --single-branch --branch main --depth=1 --filter=blob:none "$remote" "$src_tmp/checkout"
    git -C "$src_tmp/checkout" config core.autocrlf false
    if ! git -C "$src_tmp/checkout" cat-file -e "$revision^{commit}" 2>/dev/null; then git -C "$src_tmp/checkout" fetch --no-tags --depth=1 origin "$revision"; fi
    git -C "$src_tmp/checkout" checkout --detach "$revision"
    [[ ! -e $source ]] || fail 'Source appeared; refusing overwrite.'
    mv "$src_tmp/checkout" "$source"
fi
[[ -d $source/.git && ! -L $source/.git && $(git -C "$source" rev-parse HEAD) == "$revision" ]] || fail 'Existing source checkout incompatible; preserve it and correct manually.'
verify() {
    docker run --rm --network none --group-add "$BOOTSTRAP_HOST_GID" \
        --mount "type=bind,source=$1,target=/artifact,readonly" \
        --mount "type=bind,source=$source,target=/source,readonly" \
        --mount "type=bind,source=$repo/demo/src/main/resources/db/migration,target=/migrations,readonly" \
        --mount "type=bind,source=$repo/scripts/start-dev.sh,target=/startup-script,readonly" \
        --entrypoint python "$image" /app/runtime.py artifact
}
printf '[setup] Fetch demo artifacts from Catalog-Data bootstrap\n'
git -C "$source" fetch --no-tags "$remote" refs/heads/bootstrap
artifact_commit=$(git -C "$source" rev-parse 'FETCH_HEAD^{commit}')
member=demo-bootstrap/artifacts
versions=$(git -C "$source" ls-tree --name-only "$artifact_commit:$member")
missing=() found=false
while IFS= read -r release; do
    [[ $release =~ ^demo-v[1-9][0-9]*$ ]] || fail 'Unexpected artifact directory in bootstrap branch.'
    [[ $release != "$version" ]] || found=true
    [[ ! -L $base/$release ]] || fail 'Symlink artifact refused.'
    [[ -e $base/$release ]] || missing+=("$release")
done <<< "$versions"
[[ $found == true ]] || fail "Artifact $version is not published on Catalog-Data bootstrap."
if (( ${#missing[@]} )); then
    art_tmp=$(mktemp -d "$base/.artifact-setup.XXXXXXXX")
    chmod 750 "$art_tmp"
    mkdir "$art_tmp/payload"
    for release in "${missing[@]}"; do
        release_member="$member/$release"
        inventory=$(git -C "$source" ls-tree -r "$artifact_commit" -- "$release_member")
        [[ -n $inventory ]] || fail 'Empty artifact tree.'
        while IFS= read -r line; do
            [[ $line =~ ^100(644|755)' blob '[0-9a-f]+$'\t'([A-Za-z0-9._/-]+)$ ]] || fail 'Unsafe artifact Git tree.'
            relative=${BASH_REMATCH[2]}
            [[ $relative == "$release_member/"* && /$relative/ != */../* && /$relative/ != */./* ]] || fail 'Unsafe artifact path.'
        done <<< "$inventory"
        git -C "$source" -c core.autocrlf=false archive --format=tar "$artifact_commit" "$release_member" | tar -xf - --strip-components=2 -C "$art_tmp/payload"
    done
    if [[ -d $artifact ]]; then
        verify "$artifact"
    else
        verify "$art_tmp/payload/$version"
        # Docker Desktop can retain the DrvFS handle of a bind-mounted directory.
        # Move an unmounted copy instead; the installed bytes are verified again below.
        mkdir "$art_tmp/install"
        cp -a "$art_tmp/payload/$version" "$art_tmp/install/$version"
    fi
    for release in "${missing[@]}"; do
        [[ ! -e $base/$release ]] || fail 'Artifact appeared; refusing overwrite.'
        install_source="$art_tmp/payload/$release"
        [[ ! -d $art_tmp/install/$release ]] || install_source="$art_tmp/install/$release"
        mv -T "$install_source" "$base/$release"
    done
fi
verify "$artifact" || fail 'Existing artifact failed verification; preserve it and obtain a matching approved release.'
printf '[setup] Starting backend\n'
bash "$repo/scripts/start-dev.sh" "$@"
