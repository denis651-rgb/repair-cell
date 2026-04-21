$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$sourcePath = Join-Path $projectRoot "docs\Guia-Uso-MVP-YiyoTec.md"
$outputPath = Join-Path $projectRoot "docs\Guia-Uso-MVP-YiyoTec.rtf"

if (-not (Test-Path $sourcePath)) {
    throw "No se encontro el archivo fuente: $sourcePath"
}

function Convert-ToRtfEscaped {
    param([string]$Text)

    $escaped = $Text.Replace('\', '\\').Replace('{', '\{').Replace('}', '\}')
    $builder = New-Object System.Text.StringBuilder

    foreach ($char in $escaped.ToCharArray()) {
        $code = [int][char]$char
        if ($code -gt 127) {
            [void]$builder.Append("\u$code?")
        } else {
            [void]$builder.Append($char)
        }
    }

    return $builder.ToString()
}

$lines = Get-Content -Path $sourcePath -Encoding UTF8
$rtf = New-Object System.Text.StringBuilder

[void]$rtf.AppendLine('{\rtf1\ansi\deff0')
[void]$rtf.AppendLine('{\fonttbl{\f0 Calibri;}}')
[void]$rtf.AppendLine('\viewkind4\uc1\pard\lang3082\f0\fs22')

foreach ($line in $lines) {
    $trimmed = $line.Trim()

    if ($trimmed -eq '') {
        [void]$rtf.AppendLine('\par')
        continue
    }

    if ($trimmed.StartsWith('# ')) {
        $text = Convert-ToRtfEscaped ($trimmed.Substring(2))
        [void]$rtf.AppendLine("\pard\sa220\b\f0\fs36 $text\b0\fs22\par")
        continue
    }

    if ($trimmed.StartsWith('## ')) {
        $text = Convert-ToRtfEscaped ($trimmed.Substring(3))
        [void]$rtf.AppendLine("\pard\sa180\b\f0\fs28 $text\b0\fs22\par")
        continue
    }

    if ($trimmed.StartsWith('### ')) {
        $text = Convert-ToRtfEscaped ($trimmed.Substring(4))
        [void]$rtf.AppendLine("\pard\sa120\b\f0\fs24 $text\b0\fs22\par")
        continue
    }

    if ($trimmed -match '^\d+\.\s+') {
        $text = Convert-ToRtfEscaped ($trimmed -replace '^\d+\.\s+', '')
        [void]$rtf.AppendLine("\pard\li360\fi-180\sa60 $text\par")
        continue
    }

    if ($trimmed.StartsWith('- ')) {
        $text = Convert-ToRtfEscaped ($trimmed.Substring(2))
        [void]$rtf.AppendLine("\pard\li360\fi-180\sa60\'95\tab $text\par")
        continue
    }

    $text = Convert-ToRtfEscaped $trimmed
    [void]$rtf.AppendLine("\pard\sa80 $text\par")
}

[void]$rtf.AppendLine('}')

Set-Content -Path $outputPath -Value $rtf.ToString() -Encoding UTF8
Write-Output "Documento generado: $outputPath"
