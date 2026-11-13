#!/usr/bin/env bash
# Sube el código al servidor y reinicia el juego.
#   deploy/deploy.sh            pasa las pruebas y despliega
#   DEPLOY_HOST=otro deploy/deploy.sh
# Usa el alias `vps` de ~/.ssh/config. No sube el .env: la configuración
# del servidor está en /etc/nidaria/nidaria.env.
set -euo pipefail

HOST="${DEPLOY_HOST:-vps}"
APP=/opt/nidaria/app

cd "$(dirname "$0")/.."

npm test

rsync -az --delete --relative \
  package.json package-lock.json server shared public \
  "$HOST:$APP/"

ssh "$HOST" bash -s <<EOF
set -euo pipefail
chown -R nidaria:nidaria $APP
cd $APP
runuser -u nidaria -- npm ci --omit=dev --no-audit --no-fund --no-update-notifier --loglevel=error
systemctl restart nidaria
for i in \$(seq 1 20); do
  if curl -fs -o /dev/null http://127.0.0.1:3000/; then
    echo "Nidaria en marcha."
    exit 0
  fi
  sleep 1
done
echo "El juego no responde. Últimas líneas del registro:" >&2
journalctl -u nidaria -n 30 --no-pager >&2
exit 1
EOF
