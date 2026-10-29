#!/usr/bin/env bash
# Deploys main on the VPS (specs/deploy.md): pull, backup, build, start and wait for the backend.
# Tests run before merging to main (NF-03), not here.
set -Eeuo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

repo_dir=$(cd "$DEPLOY_DIR/.." && pwd)
if [[ ${1:-} != --no-pull ]]; then
    log "Bajando main"
    git -C "$repo_dir" fetch --quiet origin main
    git -C "$repo_dir" checkout --quiet main
    git -C "$repo_dir" merge --ff-only --quiet origin/main
fi
log "Versión: $(git -C "$repo_dir" log -1 --format='%h %s')"

if [[ -n $(compose ps --status running --quiet db 2> /dev/null) ]]; then
    "$DEPLOY_DIR/backup.sh" --tag pre-deploy --local-only
else
    log "La base todavía no está corriendo: primer despliegue, sin respaldo previo"
fi

log "Construyendo las imágenes"
compose build --pull
log "Levantando los servicios"
compose up -d --remove-orphans

log "Esperando a que el backend esté sano (hasta 3 minutos)"
api_id=$(compose ps --quiet api)
for _ in $(seq 1 36); do
    status=$(docker inspect --format '{{.State.Health.Status}}' "$api_id")
    if [[ $status == healthy ]]; then
        compose ps
        log "Despliegue listo"
        exit 0
    fi
    sleep 5
done

log "ERROR: el backend no quedó sano (estado: $status). Últimas líneas del log:"
compose logs --tail 80 api
exit 1
