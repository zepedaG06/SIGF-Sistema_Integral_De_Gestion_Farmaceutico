param([Parameter(Mandatory=$true)][string]$WixDirectory)
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path -Parent $PSScriptRoot)
if (-not (Test-Path (Join-Path $WixDirectory 'candle.exe')) -or -not (Test-Path (Join-Path $WixDirectory 'light.exe'))) { throw 'Indique la carpeta de binarios WiX 3.' }
if (Test-Path 'dist/SIGF-0.3.1.exe') { throw 'El instalador ya existe; muévalo antes de regenerar.' }
$env:PATH = (Resolve-Path $WixDirectory).Path + ';' + $env:PATH
mvn -q -DskipTests package dependency:copy-dependencies '-DincludeScope=runtime' '-DoutputDirectory=target/package-v031'
if ($LASTEXITCODE -ne 0) { throw 'Falló Maven.' }
Copy-Item 'target-v031/sigf-javafx-0.3.1.jar' 'target/package-v031/sigf-javafx-0.3.1.jar' -Force
jpackage --type exe --name SIGF --dest dist --input target/package-v031 --main-jar sigf-javafx-0.3.1.jar --main-class org.sigf.app.Launcher --vendor SIGF --app-version 0.3.1 --win-per-user-install --win-menu --win-shortcut
if ($LASTEXITCODE -ne 0) { throw 'Falló jpackage.' }
