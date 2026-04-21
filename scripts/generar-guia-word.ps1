$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$sourcePath = Join-Path $projectRoot "docs\Guia-Uso-MVP-YiyoTec.md"
$outputPath = Join-Path $projectRoot "docs\Guia-Uso-MVP-YiyoTec.docx"

if (-not (Test-Path $sourcePath)) {
    throw "No se encontro el archivo fuente: $sourcePath"
}

try {
    $word = New-Object -ComObject Word.Application
} catch {
    throw "Microsoft Word no esta disponible en esta maquina. No se pudo generar el archivo .docx."
}

$word.Visible = $false
$document = $word.Documents.Add()

function Set-TextFormat {
    param(
        [Parameter(Mandatory = $true)]
        $selectionObject,
        [int]$size,
        [bool]$bold = $false
    )

    $selectionObject.Font.Name = "Calibri"
    $selectionObject.Font.Size = $size
    $selectionObject.Font.Bold = [int]($bold)
    $selectionObject.ParagraphFormat.SpaceAfter = 8
}

try {
    $selection = $word.Selection
    $lines = Get-Content -Path $sourcePath -Encoding UTF8

    foreach ($line in $lines) {
        $trimmed = $line.Trim()

        if ($trimmed -eq "") {
            $selection.TypeParagraph()
            continue
        }

        if ($trimmed.StartsWith("# ")) {
            Set-TextFormat -selectionObject $selection -size 20 -bold $true
            $selection.TypeText($trimmed.Substring(2))
            $selection.TypeParagraph()
            continue
        }

        if ($trimmed.StartsWith("## ")) {
            Set-TextFormat -selectionObject $selection -size 16 -bold $true
            $selection.TypeText($trimmed.Substring(3))
            $selection.TypeParagraph()
            continue
        }

        if ($trimmed.StartsWith("### ")) {
            Set-TextFormat -selectionObject $selection -size 13 -bold $true
            $selection.TypeText($trimmed.Substring(4))
            $selection.TypeParagraph()
            continue
        }

        if ($trimmed -match '^\d+\.\s+') {
            Set-TextFormat -selectionObject $selection -size 11
            $selection.Range.ListFormat.ApplyNumberDefault() | Out-Null
            $selection.TypeText(($trimmed -replace '^\d+\.\s+', ''))
            $selection.TypeParagraph()
            $selection.Range.ListFormat.RemoveNumbers() | Out-Null
            continue
        }

        if ($trimmed.StartsWith("- ")) {
            Set-TextFormat -selectionObject $selection -size 11
            $selection.Range.ListFormat.ApplyBulletDefault() | Out-Null
            $selection.TypeText($trimmed.Substring(2))
            $selection.TypeParagraph()
            $selection.Range.ListFormat.RemoveNumbers() | Out-Null
            continue
        }

        Set-TextFormat -selectionObject $selection -size 11
        $selection.TypeText($trimmed)
        $selection.TypeParagraph()
    }

    $document.SaveAs([ref]$outputPath, [ref]16)
} finally {
    if ($document) {
        $document.Close()
    }
    if ($word) {
        $word.Quit()
    }

    [System.Runtime.Interopservices.Marshal]::ReleaseComObject($selection) | Out-Null
    [System.Runtime.Interopservices.Marshal]::ReleaseComObject($document) | Out-Null
    [System.Runtime.Interopservices.Marshal]::ReleaseComObject($word) | Out-Null
    [GC]::Collect()
    [GC]::WaitForPendingFinalizers()
}

Write-Output "Documento generado: $outputPath"
