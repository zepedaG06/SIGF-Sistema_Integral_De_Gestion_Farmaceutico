param(
 [string]$HostName='127.0.0.1', [int]$Port=5432,
 [Parameter(Mandatory=$true)][string]$DbUser,
 [Parameter(Mandatory=$true)][string]$PostgresBin,
 [string]$OutputName=('sigf-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.dump')
)
$ErrorActionPreference='Stop'
if ([IO.Path]::GetFileName($OutputName) -ne $OutputName -or -not $OutputName.EndsWith('.dump')) { throw 'Use un nombre de archivo .dump sin ruta.' }
$projectRoot=Split-Path -Parent $PSScriptRoot
$backupDir=Join-Path $projectRoot 'backups'
New-Item -ItemType Directory -Force -Path $backupDir | Out-Null
$destination=Join-Path $backupDir $OutputName
if (Test-Path -LiteralPath $destination) { throw 'El respaldo ya existe; indique otro nombre.' }
$program=Join-Path $PostgresBin 'pg_dump.exe'
if (-not (Test-Path -LiteralPath $program)) { throw 'No se encontró pg_dump.exe.' }
& $program --host $HostName --port $Port --username $DbUser --no-password --format custom --file $destination --dbname sigf_javafx_db
if ($LASTEXITCODE -ne 0) { throw 'Falló pg_dump; el archivo parcial no debe utilizarse.' }
Write-Output "Respaldo creado: $destination"
