@echo off
rem Borra los datos de SIGF local inmediatamente y sin confirmacion.
setlocal
where psql >nul 2>&1
if errorlevel 1 (
  echo No se encontro psql. Agregue la carpeta bin de PostgreSQL al PATH. 1>&2
  exit /b 1
)

rem Fijar el destino para evitar conexiones heredadas a otros servidores.
set "PGHOSTADDR="
set "PGSERVICE="
set "PGOPTIONS="
set "PGHOST="
set "PGPORT="
set "PGDATABASE="
set "PGUSER="

rem -w nunca pide contrasena; se requiere autenticacion ya configurada.
psql -X -w -1 -v ON_ERROR_STOP=1 -h 127.0.0.1 -p 5432 -U sigf_app -d sigf_javafx_db -f "%~dp0borrar_datos_sin_preguntar.sql"
if errorlevel 1 (
  echo No se borraron los datos. Revise el error anterior y cierre las demas conexiones a SIGF. 1>&2
  exit /b 1
)
echo Datos de SIGF borrados. Al conectar, la aplicacion creara nuevamente las tablas.
exit /b 0
