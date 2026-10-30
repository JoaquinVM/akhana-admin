#!/usr/bin/env bash
# Deploys a tested version on the VPS (NF-06, specs/deploy.md): checkout, build, backup, start, wait for the backend.
# Tests run in CI before a tag reaches the production branch (NF-03), not here.
# Usage: deploy.sh [--ref <production|tag|commit>] [--no-pull]
#   --ref      what to deploy; by default the production branch (moved by CI to the last tested tag)
#   --no-pull  deploys the current checkout as is (tests on the Mac)
# If the build, the start or the health check fails, it goes back to the previous version on its own: the previous
# images are kept as :rollback and, if Flyway applied a migration, the pre-deploy backup is restored first.
set -Eeuo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

ref=production
pull=true
while [[ $# -gt 0 ]]; do
    case $1 in
        --ref) ref=$2; shift 2 ;;
        --no-pull) pull=false; shift ;;
        *) echo "Opción desconocida: $1" >&2; exit 2 ;;
    esac
done

repo_dir=$(cd "$DEPLOY_DIR/.." && pwd)
state_dir=$AKHANA_DIR/state
images=(akhana-api akhana-web)
mkdir -p "$state_dir"

if command -v flock > /dev/null; then
    exec 9> "$state_dir/deploy.lock"
    flock -n 9 || { log "ERROR: ya hay un despliegue en curso"; exit 1; }
fi

git_() {
    git -C "$repo_dir" "$@"
}

# Applied migrations, to know whether a failed version changed the schema; empty without a database
flyway_state() {
    compose exec -T db psql -U "$DB_USERNAME" -d "$DB_NAME" -qAt \
        -c "SELECT count(*) || ':' || coalesce(max(installed_rank), 0) FROM flyway_schema_history" 2> /dev/null || true
}

wait_healthy() {
    local api_id status=missing
    log "Esperando a que el backend esté sano (hasta 3 minutos)"
    api_id=$(compose ps --quiet api)
    for _ in $(seq 1 36); do
        status=$(docker inspect --format '{{.State.Health.Status}}' "$api_id" 2> /dev/null || echo missing)
        [[ $status == healthy ]] && return 0
        sleep 5
    done
    log "El backend no quedó sano (estado: $status). Últimas líneas del log:"
    compose logs --tail 80 api
    return 1
}

# shellcheck disable=SC2317,SC2329  # called from the ERR trap
rollback() {
    trap - ERR
    set +e
    log "ERROR (línea $1): no se despliega ${target:0:7}; volviendo a ${previous:0:7}"
    echo "failed $target" > "$state_dir/last-result"
    if ! $has_rollback_images; then
        log "No hay imágenes anteriores (primer despliegue): revisar a mano"
        exit 1
    fi
    for image in "${images[@]}"; do
        docker tag "$image:rollback" "$image"
    done
    if ! $stopped; then
        git_ checkout --quiet --detach "$previous"
        log "Falló antes de tocar los servicios: sigue atendiendo $(git_ log -1 --format='%h %s')"
        exit 1
    fi
    compose logs --tail 80 api
    compose stop api
    if [[ -n $backup_file && $(flyway_state) != "$flyway_before" ]]; then
        log "La versión fallida cambió el esquema: restaurando $(basename "$backup_file")"
        "$DEPLOY_DIR/restore.sh" --replace "$backup_file" --yes || log "ERROR: falló la restauración"
    fi
    git_ checkout --quiet --detach "$previous"
    if compose up -d --no-build --remove-orphans && wait_healthy; then
        log "Quedó la versión anterior: $(git_ log -1 --format='%h %s')"
    else
        log "ERROR: la versión anterior tampoco quedó sana; revisar a mano (specs/deploy.md, «Volver atrás»)"
    fi
    exit 1
}

previous=$(cat "$state_dir/deployed" 2> /dev/null || git_ rev-parse HEAD)
if $pull; then
    log "Bajando $ref"
    git_ fetch --quiet --force --tags origin '+refs/heads/*:refs/remotes/origin/*'
    target=$(git_ rev-parse --verify --quiet "origin/$ref^{commit}" || git_ rev-parse --verify "$ref^{commit}")
    git_ checkout --quiet --detach "$target"
fi
target=$(git_ rev-parse HEAD)
log "Versión: $(git_ describe --tags --always) · $(git_ log -1 --format='%h %s') (antes: ${previous:0:7})"

db_running=false
[[ -n $(compose ps --status running --quiet db 2> /dev/null) ]] && db_running=true
has_rollback_images=true
for image in "${images[@]}"; do
    docker image inspect "$image" > /dev/null 2>&1 || has_rollback_images=false
done
if $has_rollback_images; then
    for image in "${images[@]}"; do
        docker tag "$image" "$image:rollback"
    done
fi
backup_file=""
flyway_before=""
stopped=false
trap 'rollback $LINENO' ERR

log "Construyendo las imágenes (la versión actual sigue atendiendo)"
compose build --pull
stopped=true

if $db_running; then
    log "Deteniendo el backend para el respaldo previo"
    compose stop api
    "$DEPLOY_DIR/backup.sh" --tag pre-deploy --local-only
    backups=("$BACKUP_DIR"/akhana-*-pre-deploy.dump)
    backup_file=${backups[${#backups[@]} - 1]}
    flyway_before=$(flyway_state)
else
    log "La base todavía no está corriendo: primer despliegue, sin respaldo previo"
fi

log "Levantando los servicios"
compose up -d --remove-orphans
wait_healthy

trap - ERR
echo "$target" > "$state_dir/deployed"
echo "ok $target" > "$state_dir/last-result"
docker image prune -f > /dev/null
compose ps
log "Despliegue listo"
