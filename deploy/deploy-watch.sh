#!/usr/bin/env bash
# Pull-based continuous delivery (NF-06, specs/deploy.md), run every 2 minutes by akhana-deploy.timer.
# When CI moves the production branch to a new tested tag, deploys it once with deploy.sh; a version that fails
# is not retried (deploy.sh already went back to the previous one). Every run pings HEALTHCHECK_DEPLOY_URL:
# red while the last deploy failed, green otherwise; no ping for a while means the VPS or the timer is down.
# Only a commit of main with a tag v* is deployed: a production branch moved by hand to anything else is refused.
set -Eeuo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

repo_dir=$(cd "$DEPLOY_DIR/.." && pwd)
state_dir=$AKHANA_DIR/state
seen_file=$state_dir/production-seen
result_file=$state_dir/last-result
mkdir -p "$state_dir"

report() {
    if [[ $(cat "$result_file" 2> /dev/null) =~ ^(failed|rejected) ]]; then
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

# The same checks as CI before it moves the branch (NF-06): a tag v* on that commit, and the commit inside main
if ! git -C "$repo_dir" fetch --quiet --force --tags origin \
        '+refs/heads/main:refs/remotes/origin/main' '+refs/heads/production:refs/remotes/origin/production'; then
    log "ERROR: no se pudieron bajar main, production y los tags"
    rm -f "$seen_file"
    ping_check "${HEALTHCHECK_DEPLOY_URL:-}" fail "No se pudieron bajar main, production y los tags desde GitHub"
    exit 1
fi
tags=$(git -C "$repo_dir" tag --points-at "$target" --list 'v*')
if [[ -z $tags ]] || ! git -C "$repo_dir" merge-base --is-ancestor "$target" origin/main; then
    reason="sin un tag v*"
    [[ -n $tags ]] && reason="con el tag $(echo "$tags" | head -1) pero fuera de main"
    log "RECHAZADO: production apunta a ${target:0:7}, $reason; no se despliega"
    echo "rejected $target" > "$result_file"
    report "Rechazado: production apunta a ${target:0:7}, $reason. Sigue la versión anterior."
    exit 1
fi
log "Tag $(echo "$tags" | head -1) sobre main: se despliega"

run_log=$(mktemp)
trap 'rm -f "$run_log"' EXIT
status=0
"$DEPLOY_DIR/deploy.sh" --ref "$target" 2>&1 | tee "$run_log" || status=$?
# deploy.sh records it too; written here as well so a rejected version before this one does not stay red
if [[ $status -eq 0 ]]; then
    echo "ok $target" > "$result_file"
else
    echo "failed $target" > "$result_file"
fi
report "$(report_lines "$run_log")"
exit "$status"
