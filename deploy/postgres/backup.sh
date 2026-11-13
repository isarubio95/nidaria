#!/usr/bin/env bash
# Copia de la base de datos del juego. La lanza nidaria-db-backup.timer cada
# noche y guarda las de los últimos 14 días en /var/backups/nidaria.
# Cómo restaurar una: README.md, sección «Base de datos en producción».
set -euo pipefail

DEST=/var/backups/nidaria
KEEP_DAYS=14
FILE="$DEST/nidaria-$(date +%Y%m%d-%H%M%S).dump"

umask 077
mkdir -p "$DEST"
trap 'rm -f "$FILE.tmp"' EXIT

docker compose -f /opt/nidaria/postgres/compose.yaml exec -T postgres \
  pg_dump -U nidaria -d nidaria --format=custom >"$FILE.tmp"
mv "$FILE.tmp" "$FILE"

find "$DEST" -name 'nidaria-*.dump' -mtime +"$KEEP_DAYS" -delete
echo "Copia guardada: $FILE ($(du -h "$FILE" | cut -f1))"
