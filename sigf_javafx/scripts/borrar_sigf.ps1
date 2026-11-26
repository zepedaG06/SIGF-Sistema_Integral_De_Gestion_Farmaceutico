[CmdletBinding()]
param()
$ErrorActionPreference='Stop'
$projectRoot=Split-Path -Parent $PSScriptRoot
foreach ($program in @('java','mvn')) { if (-not (Get-Command $program -ErrorAction SilentlyContinue)) { throw 'Se requiere JDK 21 y Maven.' } }
$work=Join-Path $projectRoot ('target/reset-'+[guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force -Path $work | Out-Null
$classpathFile=Join-Path $work 'classpath.txt'
Push-Location $projectRoot
try {
    & mvn -B "-Dsigf.build.directory=$work/build" compile dependency:build-classpath "-Dmdep.outputFile=$classpathFile"
    if ($LASTEXITCODE -ne 0) { throw 'Fallo Maven; no se restablecieron datos.' }
    $dependencies=(Get-Content -LiteralPath $classpathFile -Raw).Trim()
    & java -cp "$(Join-Path $work 'build/classes');$dependencies" org.sigf.app.ResetSigf
    if ($LASTEXITCODE -ne 0) { throw "El restablecimiento no se completo (codigo $LASTEXITCODE)." }
} finally { Pop-Location }
