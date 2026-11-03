#!/usr/bin/env bash
# Upload of the public catalog (CAT-13, specs/web-catalog.md): only outward, and only the folder the API exports.
# New photos go first and the JSON that names them after; photos no longer published are removed last.
set -Eeuo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

WEB_CATALOG_MAX_AGE_MINUTES=${WEB_CATALOG_MAX_AGE_MINUTES:-30}

if [[ -z ${WEB_CATALOG_REMOTE:-} ]]; then
    log "WEB_CATALOG_REMOTE no está definido: el catálogo web aún no se publica"
    exit 0
fi

work_dir=$(mktemp -d)
trap 'rm -rf "$work_dir"' EXIT

# shellcheck disable=SC2317,SC2329  # called from the ERR trap
on_error() {
    log "ERROR: la publicación del catálogo web falló (línea $1)"
    ping_check "${HEALTHCHECK_WEB_CATALOG_URL:-}" fail "Falló en la línea $1"
    exit 1
}
trap 'on_error $LINENO' ERR

source_dir=$(web_catalog_dir)
catalog=$source_dir/catalog.json

# A volume that is empty or lost must not empty the site: the removal below only runs after this check
if [[ ! -s $catalog ]] || ! grep -q '"products"' "$catalog"; then
    log "No hay un catálogo válido en $catalog: no se sube nada"
    false
fi
if [[ -n $(find "$catalog" -mmin +"$WEB_CATALOG_MAX_AGE_MINUTES") ]]; then
    log "El catálogo tiene más de $WEB_CATALOG_MAX_AGE_MINUTES minutos: la exportación de la API no está corriendo"
    false
fi

# The same version from start to end, although the API exports again during the upload
cp "$catalog" "$work_dir/catalog.json"

remote=${WEB_CATALOG_REMOTE%/}/
# A photo never changes under its id, so it can stay in caches; the catalog has to be fresh
$RCLONE copy "$source_dir/photos" "${remote}photos" --checksum \
    --header-upload 'Cache-Control: public, max-age=31536000, immutable'
$RCLONE copyto "$work_dir/catalog.json" "${remote}catalog.json" --checksum \
    --header-upload 'Cache-Control: public, max-age=60'
$RCLONE sync "$source_dir/photos" "${remote}photos" --checksum \
    --header-upload 'Cache-Control: public, max-age=31536000, immutable'

log "Catálogo web publicado"
ping_check "${HEALTHCHECK_WEB_CATALOG_URL:-}"
