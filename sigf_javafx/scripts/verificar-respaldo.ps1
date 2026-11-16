param(
 [string]$HostName='127.0.0.1', [int]$Port=5432,
 [Parameter(Mandatory=$true)][string]$DbUser,
 [Parameter(Mandatory=$true)][string]$PostgresBin,
 [Parameter(Mandatory=$true)][string]$BackupFile
)
$ErrorActionPreference='Stop'
$backup=(Resolve-Path -LiteralPath $BackupFile).Path
$project=(Resolve-Path -LiteralPath (Split-Path -Parent $PSScriptRoot)).Path
if (-not $backup.StartsWith($project + [IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)) { throw 'El respaldo debe estar dentro del proyecto.' }
$psql=Join-Path $PostgresBin 'psql.exe'
$restore=Join-Path $PostgresBin 'pg_restore.exe'
if (-not (Test-Path -LiteralPath $psql) -or -not (Test-Path -LiteralPath $restore)) { throw 'Faltan herramientas PostgreSQL.' }
$database='sigf_javafx_restore_test'
$count=& $psql --host $HostName --port $Port --username $DbUser --no-password --dbname $database --tuples-only --no-align --command "select count(*) from pg_class c join pg_namespace n on n.oid=c.relnamespace where n.nspname='public' and c.relkind in ('r','p','v','m','S','f')"
if ($LASTEXITCODE -ne 0) { throw 'Cree primero una base desechable vacía sigf_javafx_restore_test.' }
if (($count | Out-String).Trim() -ne '0') { throw 'La base de verificación no está vacía; no se modificó.' }
& $restore --host $HostName --port $Port --username $DbUser --no-password --dbname $database --no-owner --no-privileges --exit-on-error --single-transaction $backup
if ($LASTEXITCODE -ne 0) { throw 'Falló la restauración de prueba.' }
Write-Output 'Restauración de prueba completada en sigf_javafx_restore_test.'
