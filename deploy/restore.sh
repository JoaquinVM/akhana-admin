#!/usr/bin/env bash
# Restore of a PostgreSQL backup (NF-01, specs/deploy.md).
#   restore.sh --check [latest|<file>]   restores into a temporary database, compares it with the live one
#                                        and drops it; never touches the real database (weekly timer).
#   restore.sh --replace <file|latest>   replaces the real database (asks to type RESTAURAR).
# <file> is a local path or a file name in BACKUP_REMOTE; "latest" is the newest file in BACKUP_REMOTE.
set -Eeuo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

CHECK_DB=akhana_restore_check
mode=${1:-}
source_arg=${2:-latest}
[[ $mode == --check || $mode == --replace ]] || { sed -n '2,6p' "$0"; exit 2; }

work_dir=$(mktemp -d "${TMPDIR:-/tmp}/akhana-restore.XXXXXX")
run_log=$work_dir/run.log
trap 'rm -rf "$work_dir"' EXIT
exec > >(tee -a "$run_log") 2>&1

# shellcheck disable=SC2317  # called from the ERR trap
on_error() {
    log "ERROR: la restauración falló (línea $1)"
    [[ $mode == --check ]] && ping_check "${HEALTHCHECK_RESTORE_URL:-}" fail "$(tail -c 9000 "$run_log")"
    exit 1
}
trap 'on_error $LINENO' ERR

psql_db() {
    compose exec -T db psql -U "$DB_USERNAME" -d "$1" -v ON_ERROR_STOP=1 -qAt "${@:2}"
}

# Prints the local path of the backup to restore, downloading it when needed
fetch_backup() {
    if [[ -f $source_arg ]]; then
        echo "$source_arg"
        return
    fi
    local name=$source_arg
    if [[ $name == latest ]]; then
        name=$($RCLONE lsf --files-only "$BACKUP_REMOTE" | grep -E '^akhana-[0-9]{8}-[0-9]{4}\.dump$' | sort | tail -n 1)
        [[ -n $name ]] || { log "No hay respaldos en $BACKUP_REMOTE" >&2; return 1; }
    fi
    log "Bajando $name de $BACKUP_REMOTE" >&2
    $RCLONE copy "$BACKUP_REMOTE$name" "$work_dir"
    echo "$work_dir/$name"
}

restore_into() {
    local db=$1 file=$2
    psql_db postgres -c "DROP DATABASE IF EXISTS $db WITH (FORCE)" -c "CREATE DATABASE $db OWNER $DB_USERNAME"
    compose exec -T db pg_restore -U "$DB_USERNAME" -d "$db" --no-owner --exit-on-error < "$file"
}

SUMMARY_SQL="
SELECT 'Versión de Flyway', (SELECT max(version::int) FROM flyway_schema_history WHERE success)::text
UNION ALL SELECT 'Usuarios', count(*)::text FROM app_users
UNION ALL SELECT 'Productos', count(*)::text FROM products
UNION ALL SELECT 'Ventas', count(*)::text FROM sales
UNION ALL SELECT 'Líneas de venta', count(*)::text FROM sale_items
UNION ALL SELECT 'Movimientos', count(*)::text FROM financial_transactions
UNION ALL SELECT 'Pedidos', count(*)::text FROM purchases
UNION ALL SELECT 'Cajas', count(*)::text FROM cash_sessions
UNION ALL SELECT 'Última venta', coalesce(max(created_at)::text, '-') FROM sales"

check() {
    local file
    file=$(fetch_backup)
    ping_check "${HEALTHCHECK_RESTORE_URL:-}" start
    log "Restaurando $(basename "$file") en la base temporal $CHECK_DB"
    restore_into "$CHECK_DB" "$file"

    psql_db "$CHECK_DB" -F '|' -c "$SUMMARY_SQL" > "$work_dir/restored"
    psql_db "$DB_NAME" -F '|' -c "$SUMMARY_SQL" > "$work_dir/live"
    psql_db postgres -c "DROP DATABASE $CHECK_DB WITH (FORCE)"

    # The backup is older than the live database: its counts may be lower, never higher, and the schema is the same
    local ok=true
    printf '%-18s %22s %22s\n' "" "Respaldo" "Base viva"
    while IFS='|' read -r label restored <&3 && IFS='|' read -r _ live <&4; do
        local mark=""
        if [[ $label == "Versión de Flyway" && $restored != "$live" ]]; then
            mark="  <- distinta"
        elif [[ $restored =~ ^[0-9]+$ && $live =~ ^[0-9]+$ ]] && ((restored > live)); then
            mark="  <- revisar"
        fi
        [[ -n $mark && $label != "Versión de Flyway" ]] && ok=false
        printf '%-18s %22s %22s%s\n' "$label" "$restored" "$live" "$mark"
    done 3< "$work_dir/restored" 4< "$work_dir/live"

    if ! $ok; then
        log "La restauración no coincide con la base viva"
        false
    fi
    log "Restauración probada: el respaldo se puede usar"
    ping_check "${HEALTHCHECK_RESTORE_URL:-}" "" "$(tail -c 9000 "$run_log")"
}

replace() {
    local file
    file=$(fetch_backup)
    echo "Esto REEMPLAZA la base $DB_NAME por $(basename "$file"). Lo registrado después del respaldo se pierde"
    echo "(antes se guarda una copia de la base actual en $BACKUP_DIR)."
    read -r -p "Escribe RESTAURAR para seguir: " answer
    [[ $answer == RESTAURAR ]] || { echo "Cancelado."; exit 1; }

    log "Deteniendo el backend"
    compose stop api
    "$DEPLOY_DIR/backup.sh" --tag pre-restore --local-only
    log "Restaurando $(basename "$file") en $DB_NAME"
    restore_into "$DB_NAME" "$file"
    log "Levantando el backend"
    compose up -d api
    log "Base restaurada. Revisar el login y los reportes del último día."
}

if [[ $mode == --check ]]; then check; else replace; fi
