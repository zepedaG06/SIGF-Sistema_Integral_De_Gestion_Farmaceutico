#!/bin/sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$ROOT"
command -v mvn >/dev/null 2>&1 || { echo 'Se requiere Maven y JDK 21.' >&2; exit 1; }
exec mvn -q compile org.codehaus.mojo:exec-maven-plugin:3.5.0:java -Dexec.mainClass=org.sigf.app.ResetSigf
