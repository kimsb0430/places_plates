[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$Path
)

$ErrorActionPreference = 'Stop'
$nextRoot = (Resolve-Path -LiteralPath $Path).Path
$manifestPath = Join-Path $nextRoot 'server/app/map/page_client-reference-manifest.js'
if (-not (Test-Path -LiteralPath $manifestPath -PathType Leaf)) {
    throw 'Map client reference manifest was not found in the Next.js artifact.'
}

$manifest = Get-Content -Raw -LiteralPath $manifestPath
$entryFilesIndex = $manifest.IndexOf('"entryJSFiles"', [System.StringComparison]::Ordinal)
if ($entryFilesIndex -lt 0) {
    throw 'Next.js map entry files were not found in the client reference manifest.'
}

$entryFilesSection = $manifest.Substring($entryFilesIndex)
$mapEntryMatch = [regex]::Match(
    $entryFilesSection,
    '"\[project\]/src/app/map/page":(?<chunks>\[[^\]]+\])'
)
if (-not $mapEntryMatch.Success) {
    throw 'Next.js map entry chunk list could not be parsed.'
}

$mapEntryChunks = $mapEntryMatch.Groups['chunks'].Value | ConvertFrom-Json
$externalMapLoaderUrl = 'https://maps.googleapis.com/maps/api/js'
foreach ($chunk in $mapEntryChunks) {
    $chunkPath = Join-Path $nextRoot ($chunk -replace '/', [System.IO.Path]::DirectorySeparatorChar)
    if (-not (Test-Path -LiteralPath $chunkPath -PathType Leaf)) {
        throw "Map entry chunk is missing: $chunk"
    }
    if ((Get-Content -Raw -LiteralPath $chunkPath).Contains($externalMapLoaderUrl)) {
        throw "Google Maps loader code is included in the initial map entry chunk: $chunk"
    }
}

$dynamicLoaderChunk = Get-ChildItem -LiteralPath (Join-Path $nextRoot 'static/chunks') -File -Filter '*.js' |
    Where-Object { (Get-Content -Raw -LiteralPath $_.FullName).Contains($externalMapLoaderUrl) } |
    Select-Object -First 1
if ($null -eq $dynamicLoaderChunk) {
    throw 'A separate Google Maps loader chunk was not generated.'
}

Write-Host 'PASS: Google Maps loader is excluded from the initial map entry chunks.' -ForegroundColor Green
