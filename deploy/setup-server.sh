#!/usr/bin/env bash
# Prepares an Ubuntu 24.04 VPS for Akhana Admin (specs/deploy.md). Run as root; safe to run again.
#   setup-server.sh          packages, Docker, Tailscale, rclone, time zone, folders and backup timers
#   setup-server.sh --lock   closes every public port: only Tailscale reaches the server (run it over Tailscale)
set -euo pipefail

[[ $EUID -eq 0 ]] || { echo "Correr como root" >&2; exit 1; }
DEPLOY_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
AKHANA_DIR=${AKHANA_DIR:-/opt/akhana}

log() {
    echo "[$(date '+%H:%M:%S')] $*"
}

lock() {
    tailscale status > /dev/null 2>&1 || { echo "Tailscale no está conectado: primero 'tailscale up --ssh'" >&2; exit 1; }
    local client_ip=${SSH_CONNECTION%% *}
    if [[ -n $client_ip && ! $client_ip =~ ^100\.(6[4-9]|[7-9][0-9]|1[01][0-9]|12[0-7])\. && ${1:-} != --force ]]; then
        echo "Esta sesión SSH no entra por Tailscale ($client_ip): se cortaría al cerrar los puertos." >&2
        echo "Entrar con 'ssh root@akhana' por Tailscale (o usar --lock --force si hay consola VNC)." >&2
        exit 1
    fi
    log "Cerrando los puertos públicos"
    ufw --force reset > /dev/null
    ufw default deny incoming
    ufw default allow outgoing
    ufw allow in on tailscale0
    ufw allow 41641/udp comment 'Tailscale'
    ufw --force enable
    ufw status verbose
}

if [[ ${1:-} == --lock ]]; then
    lock "${2:-}"
    exit 0
fi

log "Actualizando paquetes"
export DEBIAN_FRONTEND=noninteractive
apt-get update -q
apt-get upgrade -yq
apt-get install -yq ca-certificates curl git ufw unattended-upgrades rclone

log "Actualizaciones de seguridad automáticas"
cat > /etc/apt/apt.conf.d/20auto-upgrades <<'CONF'
APT::Periodic::Update-Package-Lists "1";
APT::Periodic::Unattended-Upgrade "1";
CONF

log "Zona horaria America/La_Paz"
timedatectl set-timezone America/La_Paz

if ! command -v docker > /dev/null; then
    log "Instalando Docker"
    curl -fsSL https://get.docker.com | sh
fi
systemctl enable --now docker

if ! command -v tailscale > /dev/null; then
    log "Instalando Tailscale"
    curl -fsSL https://tailscale.com/install.sh | sh
fi

log "Carpetas"
install -d -m 700 "$AKHANA_DIR" /var/backups/akhana
if [[ ! -f $AKHANA_DIR/.env ]]; then
    install -m 600 "$DEPLOY_DIR/.env.production.example" "$AKHANA_DIR/.env"
    log "Creado $AKHANA_DIR/.env: completarlo antes del primer despliegue"
fi

log "Timers de respaldo, prueba de restauración y despliegue continuo"
install -m 644 "$DEPLOY_DIR"/systemd/akhana-*.service "$DEPLOY_DIR"/systemd/akhana-*.timer /etc/systemd/system/
systemctl daemon-reload
systemctl enable --now akhana-backup.timer akhana-restore-check.timer akhana-deploy.timer
systemctl list-timers 'akhana-*' --no-pager

cat <<'NEXT'

Siguientes pasos (specs/deploy.md):
  1. tailscale up --ssh --hostname akhana
  2. rclone config (remoto b2 y remoto crypt "akhana-crypt") y guardar las claves en el gestor de contraseñas
  3. completar /opt/akhana/.env: el timer akhana-deploy despliega la rama production (último tag probado)
  4. tailscale serve --bg --https=443 http://127.0.0.1:8081
  5. desde Tailscale: setup-server.sh --lock
NEXT
