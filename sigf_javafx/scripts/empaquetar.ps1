$ErrorActionPreference = 'Stop'
Set-Location (Split-Path -Parent $PSScriptRoot)
if (Test-Path 'dist/v031/SIGF') { throw 'La carpeta dist/v031/SIGF ya existe; elija otro destino o retírela manualmente antes de regenerar.' }
mvn -q -DskipTests package dependency:copy-dependencies '-DincludeScope=runtime' '-DoutputDirectory=target/package-v031'
if ($LASTEXITCODE -ne 0) { throw 'Falló Maven.' }
Copy-Item 'target-v031/sigf-javafx-0.3.1.jar' 'target/package-v031/sigf-javafx-0.3.1.jar' -Force
New-Item -ItemType Directory -Force -Path dist/v031 | Out-Null
jpackage --type app-image --name SIGF --dest dist/v031 --input target/package-v031 --main-jar sigf-javafx-0.3.1.jar --main-class org.sigf.app.Launcher --vendor SIGF --app-version 0.3.1
if ($LASTEXITCODE -ne 0) { throw 'Falló jpackage.' }
