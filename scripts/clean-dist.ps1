$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$distPath = Join-Path $projectRoot "dist"

Write-Host "Limpiando carpeta dist..."

if (Test-Path $distPath) {
    Remove-Item -Recurse -Force $distPath
}

New-Item -ItemType Directory -Force $distPath | Out-Null

Write-Host "Carpeta dist limpia."