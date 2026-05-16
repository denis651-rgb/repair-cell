$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$dbDirectory = Join-Path $projectRoot "backend\data"
$dbPath = Join-Path $dbDirectory "repair-shop-dev.db"

Write-Host "Limpiando base SQLite de desarrollo..."

if (!(Test-Path $dbDirectory)) {
    New-Item -ItemType Directory -Force $dbDirectory | Out-Null
}

$filesToDelete = @(
    $dbPath,
    "$dbPath-wal",
    "$dbPath-shm",
    "$dbPath-journal"
)

foreach ($file in $filesToDelete) {
    if (Test-Path $file) {
        Write-Host "Eliminando: $file"
        Remove-Item -Force $file
    }
}

Write-Host "Base de desarrollo limpia."
Write-Host "Se volvera a crear automaticamente al iniciar el backend."