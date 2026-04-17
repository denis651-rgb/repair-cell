$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$distPath = Join-Path $projectRoot "dist"
$winUnpackedPath = Join-Path $distPath "win-unpacked"

function Stop-LockingProcesses {
    $candidateNames = @(
        "electron",
        "Taller Celular",
        "Taller de Celulares",
        "app-builder"
    )

    foreach ($name in $candidateNames) {
        Get-Process -Name $name -ErrorAction SilentlyContinue | ForEach-Object {
            try {
                Stop-Process -Id $_.Id -Force -ErrorAction Stop
            } catch {
            }
        }
    }

    Get-CimInstance Win32_Process -ErrorAction SilentlyContinue | Where-Object {
        $_.ExecutablePath -and $_.ExecutablePath.StartsWith($winUnpackedPath, [System.StringComparison]::OrdinalIgnoreCase)
    } | ForEach-Object {
        try {
            Stop-Process -Id $_.ProcessId -Force -ErrorAction Stop
        } catch {
        }
    }
}

function Remove-DistWithRetries {
    if (-not (Test-Path $distPath)) {
        return
    }

    $attempt = 0
    $maxAttempts = 5

    while ($attempt -lt $maxAttempts) {
        try {
            Remove-Item -LiteralPath $distPath -Recurse -Force -ErrorAction Stop
            return
        } catch {
            $attempt++
            if ($attempt -ge $maxAttempts) {
                throw
            }
            Start-Sleep -Milliseconds 1200
            Stop-LockingProcesses
        }
    }
}

Stop-LockingProcesses
Remove-DistWithRetries
