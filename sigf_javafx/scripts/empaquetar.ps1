[CmdletBinding()]
param([string]$Destination = 'dist/v031')
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$output = [IO.Path]::GetFullPath($(if ([IO.Path]::IsPathRooted($Destination)) { $Destination } else { Join-Path $projectRoot $Destination }))
if (-not $output.StartsWith($projectRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) { throw 'El destino debe estar dentro del proyecto.' }
if (Test-Path -LiteralPath (Join-Path $output 'SIGF')) { throw 'La aplicacion de destino ya existe; indique otro -Destination.' }
foreach ($program in @('java','mvn','jpackage')) { if (-not (Get-Command $program -ErrorAction SilentlyContinue)) { throw "Falta $program. Se requiere JDK 21 y Maven." } }
if ([int]((& jpackage --version) -split '\.')[0] -lt 21) { throw 'Se requiere jpackage de JDK 21 o posterior.' }
[xml]$projectPom = Get-Content -LiteralPath (Join-Path $projectRoot 'pom.xml') -Raw -Encoding UTF8
$version = [string]$projectPom.project.version
$work = Join-Path $projectRoot ('target/package-' + [guid]::NewGuid().ToString('N'))
$inputDirectory = Join-Path $work 'input'
$buildDirectory = Join-Path $work 'build'
New-Item -ItemType Directory -Force -Path $inputDirectory | Out-Null
Push-Location $projectRoot
try {
    & mvn -B "-Dsigf.build.directory=$buildDirectory" verify dependency:copy-dependencies '-DincludeScope=runtime' "-DoutputDirectory=$inputDirectory"
    if ($LASTEXITCODE -ne 0) { throw 'Fallo la verificacion Maven.' }
    Copy-Item -LiteralPath (Join-Path $buildDirectory "sigf-javafx-$version.jar") -Destination $inputDirectory -ErrorAction Stop
    New-Item -ItemType Directory -Force -Path $output | Out-Null
    & jpackage --type app-image --name SIGF --dest $output --input $inputDirectory --main-jar "sigf-javafx-$version.jar" --main-class org.sigf.app.Launcher --vendor SIGF --app-version $version
    if ($LASTEXITCODE -ne 0) { throw 'Fallo jpackage.' }
    Write-Output "Aplicacion creada en $output"
} finally { Pop-Location }
