[CmdletBinding()]
param([Parameter(Mandatory=$true)][string]$WixDirectory, [string]$Destination='dist')
$ErrorActionPreference='Stop'
$projectRoot=Split-Path -Parent $PSScriptRoot
$output=[IO.Path]::GetFullPath($(if ([IO.Path]::IsPathRooted($Destination)) { $Destination } else { Join-Path $projectRoot $Destination }))
if (-not $output.StartsWith($projectRoot+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)) { throw 'El destino debe estar dentro del proyecto.' }
foreach ($program in @('candle.exe','light.exe')) { if (-not (Test-Path -LiteralPath (Join-Path $WixDirectory $program))) { throw 'Indique los binarios de WiX 3 (candle.exe y light.exe).' } }
[xml]$projectPom=Get-Content -LiteralPath (Join-Path $projectRoot 'pom.xml') -Raw -Encoding UTF8
$version=[string]$projectPom.project.version
if (Test-Path -LiteralPath (Join-Path $output "SIGF-$version.exe")) { throw 'El instalador ya existe; indique otro destino.' }
$image=Join-Path $projectRoot ('target/installer-'+[guid]::NewGuid().ToString('N'))
$previousPath=$env:PATH
try {
    & (Join-Path $PSScriptRoot 'empaquetar.ps1') -Destination $image
    $env:PATH=(Resolve-Path -LiteralPath $WixDirectory).Path+';'+$previousPath
    New-Item -ItemType Directory -Force -Path $output | Out-Null
    & jpackage --type exe --name SIGF --dest $output --app-image (Join-Path $image 'SIGF') --vendor SIGF --app-version $version --win-per-user-install --win-menu --win-shortcut
    if ($LASTEXITCODE -ne 0) { throw 'Fallo jpackage al crear el instalador.' }
} finally { $env:PATH=$previousPath }
