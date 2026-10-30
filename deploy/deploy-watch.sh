#!/usr/bin/env bash
# Pull-based continuous delivery (NF-06, specs/deploy.md), run every 2 minutes by akhana-deploy.timer.
# When CI moves the production branch to a new tested tag, deploys it once with deploy.sh; a version that fails
# is not retried (deploy.sh already went back to the previous one). Every run pings HEALTHCHECK_DEPLOY_URL:
# red while the last deploy failed, green otherwise; no ping for a while means the VPS or the timer is down.
set -Eeuo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

repo_dir=$(cd "$DEPLOY_DIR/.." && pwd)
state_dir=$AKHANA_DIR/state
seen_file=$state_dir/production-seen
result_file=$state_dir/last-result
mkdir -p "$state_dir"

report() {
    if [[ $(cat "$result_file" 2> /dev/null) == failed* ]]; then
        ping_check "${HEALTHCHECK_DEPLOY_URL:-}" fail "$1"
    else
        ping_check "${HEALTHCHECK_DEPLOY_URL:-}" "" "$1"
    fi
}

if ! heads=$(git -C "$repo_dir" ls-remote --heads origin production); then
    log "ERROR: no se pudo consultar GitHub"
    ping_check "${HEALTHCHECK_DEPLOY_URL:-}" fail "No se pudo consultar la rama production en GitHub"
    exit 1
fi
if [[ -z $heads ]]; then
    report "Todavía no hay rama production (se crea con el primer tag v* que pase CI)"
    exit 0
fi
target=${heads%%[[:space:]]*}

if [[ $target == "$(cat "$seen_file" 2> /dev/null)" ]]; then
    report "Sin cambios: production en ${target:0:7} ($(cat "$result_file" 2> /dev/null || echo 'sin despliegues'))"
    exit 0
fi
# Recorded before deploying: a failed version is tried once, not every 2 minutes
echo "$target" > "$seen_file"
log "Nueva versión en production: ${target:0:7}"

run_log=$(mktemp)
trap 'rm -f "$run_log"' EXIT
status=0
"$DEPLOY_DIR/deploy.sh" --ref "$target" 2>&1 | tee "$run_log" || status=$?
[[ $status -eq 0 ]] || echo "failed $target" > "$result_file"
report "$(tail -c 9000 "$run_log")"
exit "$status"
