#!/bin/sh
set -eu
[ "$#" -ge 2 ] && [ "$#" -le 4 ] || { echo 'Uso: verificar-respaldo.sh archivo.dump usuario [host] [puerto]' >&2; exit 1; }
root=$(CDPATH= cd -- "$(dirname "$0")/.." && pwd -P)
backup=$1; user=$2; server=${3:-127.0.0.1}; port=${4:-5432}
case "$port" in ''|*[!0-9]*) echo 'Puerto invalido' >&2; exit 1;; esac
[ "$port" -ge 1 ] && [ "$port" -le 65535 ] || { echo 'Puerto invalido' >&2; exit 1; }
[ -f "$backup" ] || { echo 'No existe el respaldo.' >&2; exit 1; }
backup=$(CDPATH= cd -- "$(dirname "$backup")" && printf '%s/%s' "$(pwd -P)" "$(basename "$backup")")
case "$backup" in "$root"/*) ;; *) echo 'El respaldo debe estar dentro del proyecto.' >&2; exit 1;; esac
for program in psql pg_restore; do command -v "$program" >/dev/null 2>&1 || { echo "Falta $program" >&2; exit 1; }; done
database=sigf_javafx_restore_test
count=$(psql -X --host "$server" --port "$port" --username "$user" --no-password --dbname "$database" --tuples-only --no-align --set ON_ERROR_STOP=1 --command "select count(*) from pg_class c join pg_namespace n on n.oid=c.relnamespace where n.nspname not like 'pg_%' and n.nspname<>'information_schema' and c.relkind in ('r','p','v','m','S','f')")
[ "$count" = '0' ] || { echo 'Cree una base desechable vacia sigf_javafx_restore_test. No se modificaron datos.' >&2; exit 1; }
pg_restore --host "$server" --port "$port" --username "$user" --no-password --dbname "$database" --no-owner --no-privileges --exit-on-error --single-transaction "$backup"
echo 'Restauracion de prueba completada en sigf_javafx_restore_test.'
