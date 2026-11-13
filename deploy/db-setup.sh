#!/usr/bin/env bash
# Monta en el servidor la base de datos del juego: PostgreSQL 18 en Docker.
#   deploy/db-setup.sh
#   DEPLOY_HOST=otro deploy/db-setup.sh
# Se puede repetir: lo que ya existe (datos, contraseñas, rol) no se toca.
#
# En el servidor queda:
#   /opt/nidaria/postgres/compose.yaml   el contenedor
#   /opt/nidaria/postgres/data/          los datos
#   /opt/nidaria/postgres/secrets/       contraseñas, solo root
#   nidaria-db.service                   arranca la base antes que el juego
#   nidaria-db-backup.timer              copia diaria en /var/backups/nidaria
#
# No cambia el DATABASE_URL del juego: al terminar dice cómo hacerlo.
set -euo pipefail

HOST="${DEPLOY_HOST:-vps}"
DIR=/opt/nidaria/postgres

cd "$(dirname "$0")"

# Sin -a: los ficheros quedan de root, no del usuario local.
ssh "$HOST" "install -d -m 755 $DIR"
rsync -rt --chmod=F644 postgres/compose.yaml "$HOST:$DIR/"
rsync -rt --chmod=F755 postgres/backup.sh "$HOST:$DIR/"
rsync -rt --chmod=F644 nidaria-db.service nidaria-db-backup.service nidaria-db-backup.timer \
  "$HOST:/etc/systemd/system/"

# Todo va entre llaves: así bash lee el script entero antes de ejecutarlo y
# `docker compose exec`, que lee de la entrada estándar, no se lo come.
ssh "$HOST" bash -s <<'EOF'
{
set -euo pipefail
DIR=/opt/nidaria/postgres
cd $DIR

if ! command -v docker >/dev/null || ! docker compose version >/dev/null 2>&1; then
  echo "Instalando Docker…"
  apt-get update -q
  DEBIAN_FRONTEND=noninteractive apt-get install -y -q docker.io docker-compose-v2
fi
systemctl enable --now docker

# Usuario con el que corre el contenedor (ver compose.yaml).
if ! id nidaria-db >/dev/null 2>&1; then
  useradd --system --no-create-home --home-dir /nonexistent --shell /usr/sbin/nologin nidaria-db
fi
printf 'PG_UID=%s\nPG_GID=%s\n' "$(id -u nidaria-db)" "$(id -g nidaria-db)" >.env
install -d -m 700 -o nidaria-db -g nidaria-db data

# Contraseñas. La del superusuario la lee el contenedor al crear la base;
# la del juego solo la usa DATABASE_URL.
umask 077
install -d -m 700 secrets
for name in postgres_password nidaria_password; do
  [ -s secrets/$name ] || openssl rand -hex 32 >secrets/$name
done
chown nidaria-db:nidaria-db secrets/postgres_password
chmod 400 secrets/postgres_password

systemctl daemon-reload
systemctl enable --now nidaria-db-backup.timer
systemctl enable nidaria-db.service
systemctl start nidaria-db.service
# Si ya estaba en marcha, aplica los cambios de compose.yaml (o no hace nada).
docker compose up --detach --wait

# Rol y base del juego. El rol no es superusuario: es dueño de su base y nada
# más. La contraseña va por la entrada estándar, no en la línea de órdenes.
sql() { docker compose exec -T postgres psql -U postgres -d postgres -v ON_ERROR_STOP=1 -qAt "$@"; }
query() { sql -c "$1" </dev/null; }
if [ "$(query "SELECT count(*) FROM pg_roles WHERE rolname = 'nidaria'")" = 0 ]; then
  sql <<SQL
CREATE ROLE nidaria LOGIN PASSWORD '$(cat secrets/nidaria_password)';
SQL
  echo "Rol nidaria creado."
fi
if [ "$(query "SELECT count(*) FROM pg_database WHERE datname = 'nidaria'")" = 0 ]; then
  query 'CREATE DATABASE nidaria OWNER nidaria'
  echo "Base nidaria creada."
fi

echo "PostgreSQL $(query 'SHOW server_version') en marcha en 127.0.0.1:5432."
if ! grep -q '^DATABASE_URL=postgresql://nidaria:.*@127\.0\.0\.1:5432/nidaria$' /etc/nidaria/nidaria.env 2>/dev/null; then
  echo
  echo "Para que el juego la use, pon en /etc/nidaria/nidaria.env:"
  echo "  DATABASE_URL=postgresql://nidaria:<contraseña>@127.0.0.1:5432/nidaria"
  echo "con la contraseña de $DIR/secrets/nidaria_password, y reinicia: systemctl restart nidaria"
fi
exit
}
EOF
