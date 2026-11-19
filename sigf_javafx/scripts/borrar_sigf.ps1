$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) { throw 'Se requiere Maven y JDK 21 para ejecutar el restablecimiento.' }
Push-Location $root
try {
    & mvn -q compile org.codehaus.mojo:exec-maven-plugin:3.5.0:java '-Dexec.mainClass=org.sigf.app.ResetSigf'
    if ($LASTEXITCODE -ne 0) { throw "El restablecimiento no se completó (código $LASTEXITCODE)." }
} finally { Pop-Location }
