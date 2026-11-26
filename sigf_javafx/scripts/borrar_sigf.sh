#!/bin/sh
set -eu
root=$(CDPATH= cd -- "$(dirname "$0")/.." && pwd -P)
cd "$root"
for program in java mvn; do command -v "$program" >/dev/null 2>&1 || { echo 'Se requiere JDK 21 y Maven.' >&2; exit 1; }; done
mkdir -p "$root/target"
work=$(mktemp -d "$root/target/reset-XXXXXXXX")
mvn -B "-Dsigf.build.directory=$work/build" compile dependency:build-classpath "-Dmdep.outputFile=$work/classpath.txt"
dependencies=$(cat "$work/classpath.txt")
exec java -cp "$work/build/classes:$dependencies" org.sigf.app.ResetSigf
