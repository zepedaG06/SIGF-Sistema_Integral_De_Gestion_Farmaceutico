#!/bin/sh
# Borra los datos de SIGF local inmediatamente y sin confirmación.
set -eu

if command -v psql >/dev/null 2>&1; then
  psql_bin=$(command -v psql)
elif [ -x "$HOME/Applications/Postgres.app/Contents/Versions/latest/bin/psql" ]; then
  psql_bin="$HOME/Applications/Postgres.app/Contents/Versions/latest/bin/psql"
elif [ -x /Applications/Postgres.app/Contents/Versions/latest/bin/psql ]; then
  psql_bin=/Applications/Postgres.app/Contents/Versions/latest/bin/psql
else
  echo 'No se encontró psql. Instale PostgreSQL o agregue psql al PATH.' >&2
  exit 1
fi

# No aceptar parámetros de conexión heredados que puedan apuntar a otro servidor.
unset PGHOSTADDR PGSERVICE PGOPTIONS PGPASSWORD PGUSER PGDATABASE PGHOST PGPORT
usuario_local=$(id -un)
"$psql_bin" -X -w -1 -v ON_ERROR_STOP=1 -h /tmp -p 5432 \
  -U "$usuario_local" -d sigf_javafx_db <<'SQL'
DO $$
BEGIN
  IF current_database() <> 'sigf_javafx_db' OR inet_server_addr() IS NOT NULL THEN
    RAISE EXCEPTION 'Destino inválido: se requiere la base local sigf_javafx_db';
  END IF;
  IF EXISTS (SELECT 1 FROM pg_stat_activity
             WHERE datname = current_database() AND pid <> pg_backend_pid()) THEN
    RAISE EXCEPTION 'Cierre SIGF y las demás conexiones a esta base antes de continuar';
  END IF;
END $$;
DROP SCHEMA public CASCADE;
CREATE SCHEMA public AUTHORIZATION pg_database_owner;
SQL

echo 'Datos de SIGF borrados. Al conectar, la aplicación creará nuevamente las tablas.'
