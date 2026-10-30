#!/usr/bin/env bash
# Daily PostgreSQL backup (NF-01, specs/deploy.md): dump, verify, upload encrypted off the VPS, prune, report.
# Usage: backup.sh [--tag pre-deploy|pre-restore] [--local-only]
#   --local-only skips the upload (pre-deploy copies, whose remote copy is tomorrow's daily one).
set -Eeuo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

tag=""
upload=true
while [[ $# -gt 0 ]]; do
    case $1 in
        --tag) tag=-$2; shift 2 ;;
        --local-only) upload=false; shift ;;
        *) echo "Opción desconocida: $1" >&2; exit 2 ;;
    esac
done

run_log=$(mktemp)
trap 'rm -f "$run_log"' EXIT
exec > >(tee -a "$run_log") 2>&1

# shellcheck disable=SC2317,SC2329  # called from the ERR trap
on_error() {
    log "ERROR: el respaldo falló (línea $1)"
    [[ -z $tag ]] && ping_check "${HEALTHCHECK_BACKUP_URL:-}" fail "$(tail -c 9000 "$run_log")"
    exit 1
}
trap 'on_error $LINENO' ERR

[[ -z $tag ]] && ping_check "${HEALTHCHECK_BACKUP_URL:-}" start

if $upload && [[ -z ${BACKUP_REMOTE:-} ]]; then
    log "BACKUP_REMOTE no está definido: el respaldo debe guardarse fuera del VPS"
    false
fi

mkdir -p "$BACKUP_DIR"
chmod 700 "$BACKUP_DIR"
file="$BACKUP_DIR/akhana-$(date '+%Y%m%d-%H%M')$tag.dump"

log "Sacando el respaldo de la base $DB_NAME en $file"
compose exec -T db pg_dump -U "$DB_USERNAME" -d "$DB_NAME" --format=custom --no-owner > "$file.partial"
mv "$file.partial" "$file"
chmod 600 "$file"

log "Verificando el archivo ($(du -h "$file" | cut -f1))"
compose exec -T db pg_restore --list < "$file" > /dev/null

if $upload; then
    log "Subiendo a $BACKUP_REMOTE"
    $RCLONE copy "$file" "$BACKUP_REMOTE"
    log "Borrando del remoto los respaldos de más de $BACKUP_KEEP_REMOTE_DAYS días"
    $RCLONE delete --min-age "${BACKUP_KEEP_REMOTE_DAYS}d" "$BACKUP_REMOTE"
fi

log "Borrando los respaldos locales de más de $BACKUP_KEEP_LOCAL_DAYS días"
find "$BACKUP_DIR" -name 'akhana-*.dump' -mtime +"$BACKUP_KEEP_LOCAL_DAYS" -delete

log "Respaldo terminado: $(basename "$file")"
[[ -z $tag ]] && ping_check "${HEALTHCHECK_BACKUP_URL:-}" "" "$(tail -c 9000 "$run_log")"
exit 0
