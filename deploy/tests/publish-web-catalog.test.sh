#!/usr/bin/env bash
# ADM-02, ADM-03: real rclone against local folders standing in for the export volume and the R2 bucket.
set -Euo pipefail

script=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/publish-web-catalog.sh
root=$(mktemp -d)
trap 'rm -rf "$root"' EXIT

failures=0
check() {
    if eval "$2"; then
        echo "ok   - $1"
    else
        echo "FAIL - $1"
        failures=$((failures + 1))
    fi
}

source_dir=$root/source
remote=$root/remote
env_file=$root/env
mkdir -p "$source_dir/photos" "$remote"
touch "$env_file"

publish() {
    ENV_FILE=$env_file AKHANA_DIR=$root WEB_CATALOG_SOURCE=$source_dir WEB_CATALOG_REMOTE=$1 bash "$script" > "$root/out" 2>&1
}

# First publication: photos and JSON arrive
echo '{"products":[{"code":"A"}]}' > "$source_dir/catalog.json"
echo image > "$source_dir/photos/a.jpg"
publish "$remote"
check "publica el catálogo" "grep -q '\"code\":\"A\"' '$remote/catalog.json'"
check "publica la foto" "[[ -f '$remote/photos/a.jpg' ]]"

# Unmarked product: its photo leaves the bucket
echo '{"products":[]}' > "$source_dir/catalog.json"
rm "$source_dir/photos/a.jpg"
publish "$remote/"
check "un catálogo vacío válido se publica" "grep -q '\"products\":\[\]' '$remote/catalog.json'"
check "la foto que ya no sale se borra" "[[ ! -e '$remote/photos/a.jpg' ]]"

# What can never happen: a lost export empties the published site
echo '{"products":[{"code":"B"}]}' > "$source_dir/catalog.json"
echo image > "$source_dir/photos/b.jpg"
publish "$remote"
rm "$source_dir/catalog.json" "$source_dir/photos/b.jpg"
publish "$remote"
check "sin catálogo falla" "[[ $? -ne 0 ]]"
check "sin catálogo no toca lo publicado" "grep -q '\"code\":\"B\"' '$remote/catalog.json' && [[ -f '$remote/photos/b.jpg' ]]"

# A broken file is not uploaded
echo 'basura' > "$source_dir/catalog.json"
publish "$remote"
check "un catálogo inválido no se sube" "grep -q '\"code\":\"B\"' '$remote/catalog.json'"

# An export that stopped: fails so the alert fires, and leaves what is published
echo '{"products":[{"code":"C"}]}' > "$source_dir/catalog.json"
touch -t 202001010000 "$source_dir/catalog.json"
publish "$remote"
result=$?
check "una exportación detenida falla" "[[ $result -ne 0 ]]"
check "una exportación detenida no sube nada" "grep -q '\"code\":\"B\"' '$remote/catalog.json'"

# Not configured yet: a normal state until the bucket exists
if ENV_FILE=$env_file AKHANA_DIR=$root WEB_CATALOG_SOURCE=$source_dir bash "$script" > "$root/out" 2>&1; then
    result=0
else
    result=1
fi
check "sin remoto configurado termina sin error" "[[ $result -eq 0 ]]"

exit "$failures"
