#!/bin/sh
set -eu
# Compatibilidad: se delega en el restablecimiento interactivo protegido.
exec sh "$(dirname "$0")/borrar_sigf.sh" "$@"
