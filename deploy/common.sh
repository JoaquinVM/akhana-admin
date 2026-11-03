# shellcheck shell=bash
# Shared settings of the deploy scripts (specs/deploy.md). Sourced, not run.
# Paths on the VPS; every one can be overridden from the environment (local tests).

AKHANA_DIR=${AKHANA_DIR:-/opt/akhana}
ENV_FILE=${ENV_FILE:-$AKHANA_DIR/.env}
DEPLOY_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
COMPOSE_PROJECT=${COMPOSE_PROJECT:-akhana}
RCLONE=${RCLONE:-rclone}

if [[ ! -r $ENV_FILE ]]; then
    echo "No se puede leer $ENV_FILE (copiar deploy/.env.production.example y completarlo)" >&2
    exit 1
fi
set -a
# shellcheck source=/dev/null
source "$ENV_FILE"
set +a

DB_NAME=${DB_NAME:-akhana}
DB_USERNAME=${DB_USERNAME:-akhana}
BACKUP_DIR=${BACKUP_DIR:-/var/backups/akhana}
BACKUP_KEEP_LOCAL_DAYS=${BACKUP_KEEP_LOCAL_DAYS:-7}
BACKUP_KEEP_REMOTE_DAYS=${BACKUP_KEEP_REMOTE_DAYS:-60}

# Copy of the product photos off the VPS (NF-08), in a folder that the 60-day cleanup of the dumps does not reach
photos_remote() {
    echo "${BACKUP_REMOTE:?BACKUP_REMOTE no está definido}product-photos/"
}

# Files of the product photos: the Docker volume, or PHOTOS_DIR in local tests
photos_dir() {
    if [[ -n ${PHOTOS_DIR:-} ]]; then
        echo "$PHOTOS_DIR"
    else
        docker volume inspect --format '{{ .Mountpoint }}' "${COMPOSE_PROJECT}_product_photos"
    fi
}

# Public copy of the catalog (CAT-13): the Docker volume, or WEB_CATALOG_SOURCE in local tests
web_catalog_dir() {
    if [[ -n ${WEB_CATALOG_SOURCE:-} ]]; then
        echo "$WEB_CATALOG_SOURCE"
    else
        docker volume inspect --format '{{ .Mountpoint }}' "${COMPOSE_PROJECT}_web_catalog"
    fi
}

compose() {
    docker compose -p "$COMPOSE_PROJECT" -f "$DEPLOY_DIR/docker-compose.prod.yml" --env-file "$ENV_FILE" "$@"
}

log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*"
}

# Pings healthchecks.io: ping <url> [start|fail] [message]. An empty URL means no alerts.
ping_check() {
    local url=$1 suffix=${2:-} body=${3:-}
    [[ -z $url ]] && return 0
    curl -fsS -m 10 --retry 3 --data-raw "$body" "$url${suffix:+/$suffix}" > /dev/null || log "Aviso: no se pudo avisar a healthchecks.io"
}
