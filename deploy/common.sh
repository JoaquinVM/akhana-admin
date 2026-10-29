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
