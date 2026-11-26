#!/bin/sh
set -eu
root=$(CDPATH= cd -- "$(dirname "$0")/.." && pwd -P)
cd "$root"
for program in java mvn jpackage; do command -v "$program" >/dev/null 2>&1 || { echo "Falta $program; se requiere JDK 21 y Maven." >&2; exit 1; }; done
case "$(uname -s)" in Linux) platform=linux;; Darwin) platform=macos;; *) echo 'Use empaquetar.ps1 en Windows.' >&2; exit 1;; esac
major=$(jpackage --version | cut -d. -f1)
[ "$major" -ge 21 ] || { echo 'Se requiere jpackage de JDK 21 o posterior.' >&2; exit 1; }
architecture=$(java -XshowSettings:properties -version 2>&1 | awk '/os.arch =/ {print $3; exit}')
case "$architecture" in aarch64|arm64) architecture=arm64;; amd64|x86_64) architecture=x64;; *) echo 'Arquitectura Java no admitida.' >&2; exit 1;; esac
output="$root/dist/v031-$platform-$architecture"
if [ "$#" -gt 0 ]; then
  [ "$#" -eq 2 ] && [ "$1" = '--destination' ] || { echo 'Uso: empaquetar.sh [--destination carpeta]' >&2; exit 1; }
  case "$2" in /*) output=$2;; *) output="$root/$2";; esac
fi
case "$output" in "$root"/*) ;; *) echo 'El destino debe estar dentro del proyecto.' >&2; exit 1;; esac
case "$output" in */../*|*/..) echo 'El destino no puede contener ..' >&2; exit 1;; esac
[ ! -e "$output/SIGF" ] && [ ! -e "$output/SIGF.app" ] || { echo 'La aplicacion de destino ya existe.' >&2; exit 1; }
version=$(sed -n 's/.*<version>\([^<]*\)<\/version>.*/\1/p' pom.xml | sed -n '1p')
[ -n "$version" ] || { echo 'No se pudo leer la version Maven.' >&2; exit 1; }
mkdir -p "$root/target"
work=$(mktemp -d "$root/target/package-XXXXXXXX")
mkdir -p "$work/input"
mvn -B "-Dsigf.build.directory=$work/build" verify dependency:copy-dependencies -DincludeScope=runtime "-DoutputDirectory=$work/input"
cp "$work/build/sigf-javafx-$version.jar" "$work/input/"
mkdir -p "$output"
jpackage --type app-image --name SIGF --dest "$output" --input "$work/input" --main-jar "sigf-javafx-$version.jar" --main-class org.sigf.app.Launcher --vendor SIGF --app-version "$version"
echo "Aplicacion creada en $output"
