# Arranca todo el sistema: compila el backend, instala el frontend y abre
# SRI Service (8081), ANT Service (8082) y la Web App (5173) en ventanas separadas.
$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot

# Recarga el PATH de Windows (por si Node/Java se instalaron con la terminal ya abierta
# o si se lanza desde bash/MSYS2, que no hereda el PATH completo).
$env:Path = [Environment]::GetEnvironmentVariable('Path', 'Machine') + ';' +
            [Environment]::GetEnvironmentVariable('Path', 'User') + ';' + $env:Path

if (-not (Test-Path "$root\.env")) { Copy-Item "$root\.env.example" "$root\.env" }

if (-not (Get-Command java -ErrorAction SilentlyContinue)) { throw 'Falta Java 21 (https://adoptium.net).' }
if (-not (Get-Command npm -ErrorAction SilentlyContinue)) {
    throw 'Falta Node.js 18+. Instalalo con:  winget install OpenJS.NodeJS.LTS  (y abre una terminal nueva).'
}

if (Select-String -Path "$root\.env" -Pattern '^REDIS_URL=redis://localhost' -Quiet) {
    Write-Warning "REDIS_URL en .env apunta a localhost: edita .env y pon la URL de tu Redis en la nube (sin ella no hay cache)."
}

Write-Host '== Compilando backend (Maven Wrapper)...' -ForegroundColor Cyan
Push-Location "$root\backend"
& .\mvnw.cmd -q -B -DskipTests package
if ($LASTEXITCODE -ne 0) { Pop-Location; throw 'Fallo la compilacion del backend.' }
Pop-Location

if (-not (Test-Path "$root\frontend\node_modules")) {
    Write-Host '== Instalando dependencias del frontend...' -ForegroundColor Cyan
    Push-Location "$root\frontend"
    npm install --no-audit --no-fund
    Pop-Location
}

Write-Host '== Iniciando servicios...' -ForegroundColor Cyan
Start-Process powershell -WorkingDirectory $root -ArgumentList '-NoExit', '-Command', '$Host.UI.RawUI.WindowTitle=''SRI Service :8081''; java -jar backend\sri-service\target\sri-service-1.0.0.jar'
Start-Process powershell -WorkingDirectory $root -ArgumentList '-NoExit', '-Command', '$Host.UI.RawUI.WindowTitle=''ANT Service :8082''; java -jar backend\ant-service\target\ant-service-1.0.0.jar'
Start-Process powershell -WorkingDirectory "$root\frontend" -ArgumentList '-NoExit', '-Command', '$Host.UI.RawUI.WindowTitle=''Web App :5173''; npm run dev'

Write-Host ''
Write-Host 'Listo. Abre http://localhost:5173 cuando los servicios terminen de arrancar (~15 s).' -ForegroundColor Green
Write-Host 'Para detener: cierra las tres ventanas.'
