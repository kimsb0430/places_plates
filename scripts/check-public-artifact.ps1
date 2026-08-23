[CmdletBinding()]
param([Parameter(Mandatory = $true)][string]$Path)

$ErrorActionPreference = 'Stop'
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$frontendRoot = [System.IO.Path]::GetFullPath((Join-Path $repositoryRoot 'frontend'))
$artifactRoot = (Resolve-Path -LiteralPath $Path).Path
$expectedRoot = [System.IO.Path]::GetFullPath((Join-Path $frontendRoot 'dist'))

if (-not $artifactRoot.Equals($expectedRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw 'Public artifact path must be frontend/dist.'
}

$secretPatterns = @(
    '-----BEGIN (RSA |EC |OPENSSH |DSA )?PRIVATE KEY-----',
    '\bsk-[A-Za-z0-9_-]{20,}\b',
    '\bgh[pousr]_[A-Za-z0-9]{20,}\b',
    '\b(AKIA|ASIA)[A-Z0-9]{16}\b',
    '\bAIza[A-Za-z0-9_-]{30,}\b',
    '\bxox[baprs]-[A-Za-z0-9-]{20,}\b'
)
$textExtensions = @('.html', '.css', '.js', '.mjs', '.cjs', '.json', '.map', '.txt', '.xml', '.svg')
$findings = [System.Collections.Generic.List[string]]::new()

foreach ($file in Get-ChildItem -LiteralPath $artifactRoot -Recurse -File) {
    if ($textExtensions -notcontains $file.Extension.ToLowerInvariant()) {
        continue
    }
    $content = Get-Content -Raw -LiteralPath $file.FullName
    foreach ($pattern in $secretPatterns) {
        if ($content -match $pattern) {
            $findings.Add($file.FullName.Substring($artifactRoot.Length).TrimStart('\'))
            break
        }
    }
}

if ($findings.Count -gt 0) {
    throw "Potential secrets detected in public artifact: $($findings -join ', ')"
}

Write-Host 'PASS: Public artifact contains no high-confidence secret patterns.' -ForegroundColor Green
