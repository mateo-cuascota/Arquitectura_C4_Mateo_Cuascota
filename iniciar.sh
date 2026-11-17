#!/usr/bin/env bash
# Inicia todo el sistema desde una terminal bash (Git Bash / MSYS2 / terminal de VS Code).
# Uso:  ./iniciar.sh
set -e
cd "$(dirname "$0")"

# Crea .env a partir de la plantilla si todavia no existe.
if [ ! -f .env ]; then
  cp .env.example .env
  echo "Se creo .env: edita REDIS_URL con la URL de tu Redis en la nube."
fi

PS="powershell.exe"
command -v "$PS" >/dev/null 2>&1 || PS="/c/Windows/System32/WindowsPowerShell/v1.0/powershell.exe"

"$PS" -NoProfile -ExecutionPolicy Bypass -File "$(cygpath -w "$PWD/iniciar.ps1" 2>/dev/null || echo iniciar.ps1)"
