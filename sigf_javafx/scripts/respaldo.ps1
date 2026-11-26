[CmdletBinding()]
param([string]$HostName='127.0.0.1', [ValidateRange(1,65535)][int]$Port=5432,
 [Parameter(Mandatory=$true)][string]$DbUser,
 [Parameter(Mandatory=$true)][string]$PostgresBin,
 [string]$OutputName=('sigf-'+(Get-Date -Format 'yyyyMMdd-HHmmss')+'.dump'))
$ErrorActionPreference='Stop'
if ([IO.Path]::GetFileName($OutputName) -ne $OutputName -or -not $OutputName.EndsWith('.dump') -or $OutputName.IndexOfAny([IO.Path]::GetInvalidFileNameChars()) -ge 0) { throw 'Use un nombre .dump valido, sin ruta.' }
$backupDir=Join-Path (Split-Path -Parent $PSScriptRoot) 'backups'
New-Item -ItemType Directory -Force -Path $backupDir | Out-Null
$destination=Join-Path $backupDir $OutputName
if (Test-Path -LiteralPath $destination) { throw 'El respaldo ya existe; indique otro nombre.' }
$program=Join-Path $PostgresBin 'pg_dump.exe'
if (-not (Test-Path -LiteralPath $program)) { throw 'No se encontro pg_dump.exe.' }
$partial=Join-Path $backupDir ('.partial-'+[guid]::NewGuid().ToString('N'))
& $program --host $HostName --port $Port --username $DbUser --no-password --format custom --no-owner --no-privileges --file $partial --dbname sigf_javafx_db
if ($LASTEXITCODE -ne 0) { throw "Fallo pg_dump. No se publico un respaldo valido; parcial: $partial" }
if ((Get-Item -LiteralPath $partial).Length -eq 0) { throw 'El respaldo esta vacio.' }
# Ambos archivos estan dentro de backups; Move-Item no reemplaza un archivo existente.
Move-Item -LiteralPath $partial -Destination $destination -ErrorAction Stop
Write-Output "Respaldo creado: $destination"
