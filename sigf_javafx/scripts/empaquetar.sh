#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
case "$(uname -s)" in Linux) platform=linux;; Darwin) platform=macos;; *) echo "Sistema no admitido por este script" >&2; exit 1;; esac
architecture=$(uname -m)
output="dist/v031-$platform-$architecture"
if [ -e "$output" ]; then echo "Ya existe $output; mueva la distribución anterior antes de regenerar." >&2; exit 1; fi
mvn -q -DskipTests package dependency:copy-dependencies -DincludeScope=runtime "-DoutputDirectory=target/package-v031-$platform-$architecture"
cp target-v031/sigf-javafx-0.3.1.jar "target/package-v031-$platform-$architecture/"
mkdir -p "$output"
jpackage --type app-image --name SIGF --dest "$output" --input "target/package-v031-$platform-$architecture" --main-jar sigf-javafx-0.3.1.jar --main-class org.sigf.app.Launcher --vendor SIGF --app-version 0.3.1
echo "Aplicación creada en $output"
