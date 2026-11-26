#!/bin/sh
set -eu
root=$(CDPATH= cd -- "$(dirname "$0")/.." && pwd -P)
[ "$#" -ge 1 ] && [ "$#" -le 4 ] || { echo 'Uso: respaldo.sh usuario [host] [puerto] [nombre.dump]' >&2; exit 1; }
user=$1; server=${2:-127.0.0.1}; port=${3:-5432}; name=${4:-sigf-$(date +%Y%m%d-%H%M%S).dump}
case "$port" in ''|*[!0-9]*) echo 'Puerto invalido' >&2; exit 1;; esac
[ "$port" -ge 1 ] && [ "$port" -le 65535 ] || { echo 'Puerto invalido' >&2; exit 1; }
case "$name" in */*|*\\*|.*|''|*:) echo 'Nombre invalido.' >&2; exit 1;; *.dump) ;; *) echo 'Use un nombre .dump sin ruta.' >&2; exit 1;; esac
command -v pg_dump >/dev/null 2>&1 || { echo 'Agregue pg_dump al PATH.' >&2; exit 1; }
mkdir -p "$root/backups"
destination="$root/backups/$name"
[ ! -e "$destination" ] || { echo 'El respaldo ya existe.' >&2; exit 1; }
partial=$(mktemp "$root/backups/.partial-XXXXXXXX")
pg_dump --host "$server" --port "$port" --username "$user" --no-password --format custom --no-owner --no-privileges --file "$partial" --dbname sigf_javafx_db
[ -s "$partial" ] || { echo 'El respaldo esta vacio.' >&2; exit 1; }
# Publicacion atomica sin reemplazar otro respaldo. APFS y los sistemas Linux admiten enlaces duros.
ln "$partial" "$destination"
rm "$partial"
echo "Respaldo creado: $destination"
